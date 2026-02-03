package com.mooncell.gateway.core.stream;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StreamBridge {

    // TaskID -> Sink
    private final Map<String, Sinks.Many<String>> sinks = new ConcurrentHashMap<>();

    public Sinks.Many<String> createSink(String taskId) {
        // 使用 unicast，因为通常只有一个 HTTP 连接在等待 SSE
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        sinks.put(taskId, sink);
        return sink;
    }

    public void emit(String taskId, String data) {
        Sinks.Many<String> sink = sinks.get(taskId);
        if (sink != null) {
            sink.tryEmitNext(data);
        }
    }

    public void complete(String taskId) {
        Sinks.Many<String> sink = sinks.get(taskId);
        if (sink != null) {
            sink.tryEmitComplete();
            sinks.remove(taskId);
        }
    }

    public void error(String taskId, Throwable t) {
        Sinks.Many<String> sink = sinks.get(taskId);
        if (sink != null) {
            sink.tryEmitError(t);
            sinks.remove(taskId);
        }
    }
    
    public Flux<String> getFlux(String taskId) {
        Sinks.Many<String> sink = sinks.get(taskId);
        return sink != null ? sink.asFlux() : Flux.error(new RuntimeException("Task not found"));
    }
}

