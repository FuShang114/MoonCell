package com.mooncell.gateway.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对应数据库中的 model_instance 表
 * 每一个对象代表一个可用的大模型服务节点 (URL级别)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInstance {
    private Long id;
    private Long providerId;
    private String providerName; // 冗余字段，方便使用
    
    private String modelName; // e.g., "gpt-4"
    private String url;       // 核心唯一标识, e.g. "https://api.openai.com/v1"
    private String apiKey;    // 对应的 Key
    
    private Integer weight;   // 权重
    private Boolean isActive; // 数据库中的配置状态
    
    // --- 运行时状态 (不持久化到 DB，或者异步持久化) ---
    
    @Builder.Default
    private transient AtomicInteger failureCount = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicInteger requestCount = new AtomicInteger(0);
    @Builder.Default
    private transient AtomicLong totalLatency = new AtomicLong(0);
    @Builder.Default
    private transient long lastUsedTime = System.currentTimeMillis();
    @Builder.Default
    private transient long lastFailureTime = 0;
    
    // 熔断标志 (内存态，与 isActive 结合使用)
    @Builder.Default
    private transient volatile boolean circuitOpen = false;

    public void recordSuccess(long latency) {
        this.circuitOpen = false;
        this.failureCount.set(0);
        this.requestCount.incrementAndGet();
        this.totalLatency.addAndGet(latency);
        this.lastUsedTime = System.currentTimeMillis();
    }

    public void recordFailure() {
        int failures = this.failureCount.incrementAndGet();
        this.lastFailureTime = System.currentTimeMillis();
        if (failures >= 3) {
            this.circuitOpen = true;
        }
    }
    
    // 判断节点是否真实可用 (DB配置开启 + 熔断器未开启)
    public boolean isHealthy() {
        return Boolean.TRUE.equals(isActive) && !circuitOpen;
    }
}

