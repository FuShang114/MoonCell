package com.mooncell.gateway.web;

import com.mooncell.gateway.api.OpenAiRequest;
import com.mooncell.gateway.core.stream.StreamBridge;
import com.mooncell.gateway.core.task.TaskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
@RequiredArgsConstructor
@Slf4j
public class GatewayController {

    private final TaskManager taskManager;
    private final StreamBridge streamBridge;

    /**
     * 统一入口：接收 OpenAI 格式请求 -> 转为任务 -> SSE 返回
     */
    @PostMapping(value = "/v1/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody OpenAiRequest request) {
        log.info("Received request for model: {}", request.getModel());
        
        // 1. 提交任务 (持久化 + 入队)
        String taskId = taskManager.submit(request);
        
        // 2. 创建 SSE 管道
        Sinks.Many<String> sink = streamBridge.createSink(taskId);
        
        // 3. 返回 Flux
        return sink.asFlux()
                .doOnCancel(() -> {
                    log.warn("Client cancelled request: {}", taskId);
                    // 这里可以触发 TaskManager 取消任务逻辑（可选）
                });
    }
}

