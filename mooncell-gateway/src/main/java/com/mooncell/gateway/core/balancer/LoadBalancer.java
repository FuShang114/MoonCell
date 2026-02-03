package com.mooncell.gateway.core.balancer;

import com.mooncell.gateway.core.cache.ModelCacheService;
import com.mooncell.gateway.core.model.ModelInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LoadBalancer {

    private final ModelCacheService cacheService;
    private final AtomicInteger counter = new AtomicInteger(0);

    // 获取指定模型的下一个可用 Instance
    public ModelInstance next(String modelName) {
        List<ModelInstance> instances = cacheService.getInstances(modelName);
        
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException("No instances configured for model: " + modelName);
        }

        // 1. 过滤出健康的 Instance (DB active + 熔断未开)
        List<ModelInstance> healthyInstances = instances.stream()
                .filter(ModelInstance::isHealthy)
                .collect(Collectors.toList());

        if (healthyInstances.isEmpty()) {
            // 降级策略：尝试选取一个虽然熔断但 failureCount 最少的，给一次重试机会
            return instances.stream()
                    .min((k1, k2) -> Integer.compare(k1.getFailureCount().get(), k2.getFailureCount().get()))
                    .orElseThrow(() -> new RuntimeException("All instances are down for model: " + modelName));
        }

        // 2. 加权轮询 (Weighted Round Robin) - 简化版
        // 实际生产可以使用 Nginx 的平滑加权轮询算法
        int index = Math.abs(counter.getAndIncrement() % healthyInstances.size());
        return healthyInstances.get(index);
    }
}
