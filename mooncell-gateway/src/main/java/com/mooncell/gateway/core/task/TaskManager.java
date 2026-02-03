package com.mooncell.gateway.core.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mooncell.gateway.api.OpenAiRequest;
import com.mooncell.gateway.core.dao.TaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskManager {

    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    
    // 内存阻塞队列
    private final BlockingQueue<ChatTask> taskQueue = new LinkedBlockingQueue<>(10000);

    @PostConstruct
    public void recover() {
        log.info("Recovering pending tasks from database...");
        List<ChatTask> pendingTasks = taskMapper.findPendingTasks();
        for (ChatTask task : pendingTasks) {
            if (taskQueue.offer(task)) {
                log.info("Recovered task: {}", task.getId());
            } else {
                log.warn("Queue full during recovery, task {} skipped", task.getId());
            }
        }
    }

    public String submit(OpenAiRequest request) {
        // 1. 幂等性检查
        String idempotencyKey = request.getIdempotencyKey();
        if (idempotencyKey != null) {
            ChatTask existing = taskMapper.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                log.info("Idempotent hit: {}", idempotencyKey);
                return existing.getId();
            }
        } else {
            // 如果没有传，生成一个默认的避免空指针，或者允许为 null
            idempotencyKey = UUID.randomUUID().toString();
        }

        String taskId = UUID.randomUUID().toString();
        try {
            String json = objectMapper.writeValueAsString(request);
            
            ChatTask task = ChatTask.builder()
                    .id(taskId)
                    .idempotencyKey(idempotencyKey)
                    .model(request.getModel())
                    .requestJson(json)
                    .status("PENDING")
                    .build();
            
            // 2. 持久化 (WAL) - 处理并发冲突
            try {
                taskMapper.insert(task);
            } catch (DuplicateKeyException e) {
                // 并发情况下可能刚查没有，现在有了
                ChatTask existing = taskMapper.findByIdempotencyKey(idempotencyKey);
                if (existing != null) return existing.getId();
                throw e;
            }
            
            // 3. 入队
            if (!taskQueue.offer(task)) {
                // 队列满，系统过载。
                // 标记为 FAILED，避免下次恢复时积压，或者让客户端重试
                taskMapper.compareAndSetStatus(taskId, "PENDING", "FAILED");
                throw new RuntimeException("System Busy: Task queue full");
            }
            
            return taskId;
        } catch (Exception e) {
            log.error("Failed to submit task", e);
            throw new RuntimeException(e);
        }
    }
    
    public ChatTask take() throws InterruptedException {
        return taskQueue.take();
    }
    
    // 使用 CAS 更新状态
    public boolean updateStatus(String taskId, String expect, String next) {
        return taskMapper.compareAndSetStatus(taskId, expect, next) > 0;
    }
}
