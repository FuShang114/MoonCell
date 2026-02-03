package com.mooncell.gateway.core.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mooncell.gateway.core.balancer.LoadBalancer;
import com.mooncell.gateway.core.balancer.ResourceLockManager;
import com.mooncell.gateway.core.model.ModelInstance;
import com.mooncell.gateway.core.stream.StreamBridge;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskConsumer {

    private final TaskManager taskManager;
    private final LoadBalancer loadBalancer;
    private final ResourceLockManager lockManager;
    private final StreamBridge streamBridge;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private final ExecutorService executor = Executors.newFixedThreadPool(20);

    @PostConstruct
    public void start() {
        new Thread(this::consumeLoop, "TaskConsumer-Thread").start();
    }

    private void consumeLoop() {
        while (true) {
            try {
                ChatTask task = taskManager.take();
                executor.submit(() -> processTask(task));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in consumer loop", e);
            }
        }
    }

    private void processTask(ChatTask task) {
        if (!taskManager.updateStatus(task.getId(), "PENDING", "RUNNING")) {
            log.warn("Task {} status invalid, skip", task.getId());
            return;
        }

        ModelInstance instance = null;
        boolean asyncStarted = false; // 标记是否成功进入异步流

        try {
            JsonNode requestJson = objectMapper.readTree(task.getRequestJson());
            
            instance = loadBalancer.next(task.getModel());
            
            // Fast Fail at Lock Acquisition
            if (!lockManager.tryLock(instance)) {
                throw new RuntimeException("Server Busy: Resource limit reached for " + instance.getUrl());
            }

            String targetUrl = buildTargetUrl(instance);
            ModelInstance finalInstance = instance;

            webClientBuilder.build()
                    .post()
                    .uri(targetUrl)
                    .headers(h -> {
                        h.setBearerAuth(finalInstance.getApiKey());
                        if ("azure".equalsIgnoreCase(finalInstance.getProviderName())) {
                            h.set("api-key", finalInstance.getApiKey());
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(chunk -> streamBridge.emit(task.getId(), chunk))
                    .doOnComplete(() -> {
                        streamBridge.complete(task.getId());
                        taskManager.updateStatus(task.getId(), "RUNNING", "COMPLETED");
                        finalInstance.recordSuccess(0);
                    })
                    .doOnError(e -> {
                        streamBridge.error(task.getId(), e);
                        taskManager.updateStatus(task.getId(), "RUNNING", "FAILED");
                        finalInstance.recordFailure();
                    })
                    .doOnTerminate(() -> {
                        // 异步流结束时释放锁
                        lockManager.release(finalInstance);
                    })
                    .subscribe();
            
            asyncStarted = true;

        } catch (Exception e) {
            log.error("Task failed synchronously: " + task.getId(), e);
            taskManager.updateStatus(task.getId(), "RUNNING", "FAILED");
            streamBridge.error(task.getId(), e);
            
            // 如果还没开始异步流，说明是在同步阶段挂了，需要手动释放锁
            if (!asyncStarted && instance != null) {
                lockManager.release(instance);
            }
        }
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
