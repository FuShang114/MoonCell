package com.mooncell.gateway.core.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.mooncell.gateway.core.dao.ModelInstanceMapper;
import com.mooncell.gateway.core.model.ModelInstance;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCacheService {

    private final ModelInstanceMapper modelMapper;

    // 核心缓存：ModelName -> List<Instances>
    private LoadingCache<String, List<ModelInstance>> cache;
    
    // 运行时状态保持器 (URL -> State)
    // 防止缓存刷新导致熔断状态丢失
    private final Map<String, InstanceRuntimeState> runtimeStates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 初始化 Caffeine 缓存
        // refreshAfterWrite: 自动异步刷新，不会阻塞读请求（除非是第一次加载）
        cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .refreshAfterWrite(1, TimeUnit.MINUTES) // 每分钟自动从 DB 同步一次
                .build(this::loadFromDb); // 传入 Callable (Loader)
    }

    /**
     * CacheLoader 的实现：从 DB 加载数据，并注入运行时状态
     */
    private List<ModelInstance> loadFromDb(String modelName) {
        log.debug("Loading model instances from DB for: {}", modelName);
        List<ModelInstance> instances = modelMapper.findByModelName(modelName);
        
        // 注入运行时状态
        instances.forEach(this::injectRuntimeState);
        return instances;
    }

    /**
     * 将运行时状态注入到新加载的 POJO 中
     */
    private void injectRuntimeState(ModelInstance instance) {
        InstanceRuntimeState state = runtimeStates.computeIfAbsent(instance.getUrl(), k -> new InstanceRuntimeState());
        instance.setFailureCount(state.failureCount);
        instance.setRequestCount(state.requestCount);
        instance.setTotalLatency(state.totalLatency);
        instance.setLastUsedTime(state.lastUsedTime);
        instance.setLastFailureTime(state.lastFailureTime);
        // 注意：circuitOpen 是一个 volatile 状态，这里我们简单处理，
        // 也可以选择不持久化熔断状态，让其重新探测
        instance.setCircuitOpen(state.circuitOpen); 
    }
    
    /**
     * 对外暴露的获取接口
     */
    public List<ModelInstance> getInstances(String modelName) {
        return cache.get(modelName);
    }

    /**
     * 强制刷新缓存 (当 Admin API 更新配置时调用)
     * "刷新中断队列" 的效果通过 cache.refresh 实现，它会异步加载新值，
     * 旧值在加载完成前依然可用，实现了 Copy-On-Write 的效果，不会阻塞。
     */
    public void refresh(String modelName) {
        log.info("Refreshing cache for model: {}", modelName);
        cache.refresh(modelName);
    }
    
    public Map<String, List<ModelInstance>> getAllCached() {
        return cache.asMap();
    }

    // 内部类：保存运行时状态
    private static class InstanceRuntimeState {
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger requestCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        long lastUsedTime = System.currentTimeMillis();
        long lastFailureTime = 0;
        volatile boolean circuitOpen = false;
    }
    
    // 当业务层更新了 ModelInstance 的状态时，同步更新 RuntimeMap
    // 其实 ModelInstance 对象本身就持有这些引用，但为了以防对象被替换，保留这个 Map 是安全的
}

