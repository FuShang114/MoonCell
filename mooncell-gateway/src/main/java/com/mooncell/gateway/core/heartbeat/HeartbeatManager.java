package com.mooncell.gateway.core.heartbeat;

import com.mooncell.gateway.core.cache.ModelCacheService;
import com.mooncell.gateway.core.model.ModelInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatManager {

    private final ModelCacheService cacheService;
    private final WebClient.Builder webClientBuilder;

    // 每 30 秒执行一次心跳检测
    @Scheduled(fixedRate = 30000)
    public void checkHealth() {
        // 从缓存获取当前所有的配置快照
        Map<String, List<ModelInstance>> allModels = cacheService.getAllCached();
        
        allModels.forEach((modelName, instances) -> {
            instances.forEach(instance -> {
                // 如果熔断开启，或者长时间未用，则探测
                if (!instance.isHealthy() || (System.currentTimeMillis() - instance.getLastUsedTime() > 60000)) {
                    performHeartbeat(instance);
                }
            });
        });
    }

    private void performHeartbeat(ModelInstance instance) {
        String payload = """
            {
                "model": "%s",
                "messages": [{"role": "user", "content": "ping"}],
                "max_tokens": 1
            }
        """.formatted(instance.getModelName());

        String targetUrl = buildTargetUrl(instance);

        webClientBuilder.build().post()
                .uri(targetUrl)
                .headers(headers -> {
                    if ("azure".equalsIgnoreCase(instance.getProviderName())) {
                        headers.add("api-key", instance.getApiKey());
                    } else {
                        headers.add("Authorization", "Bearer " + instance.getApiKey());
                    }
                    headers.add("Content-Type", "application/json");
                })
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> {
                            if (response.getStatusCode().is2xxSuccessful()) {
                                log.info("Instance {} recovered.", instance.getUrl());
                                // 成功，重置状态
                                // 注意：因为引用的是 Cache 中的对象，所以这里的修改是内存态的
                                // 只要 ModelCacheService.runtimeStates 保持引用，状态就能保留
                                instance.recordSuccess(0);
                            } else {
                                log.warn("Instance {} heartbeat failed: {}", instance.getUrl(), response.getStatusCode());
                            }
                        },
                        error -> log.error("Instance {} heartbeat error: {}", instance.getUrl(), error.getMessage())
                );
    }
    
    private String buildTargetUrl(ModelInstance instance) {
        String base = instance.getUrl();
        if (!base.endsWith("/chat/completions") && !"azure".equalsIgnoreCase(instance.getProviderName())) {
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + "/chat/completions";
        }
        return base;
    }
}
