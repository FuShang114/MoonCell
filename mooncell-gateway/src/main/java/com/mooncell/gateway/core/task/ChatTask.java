package com.mooncell.gateway.core.task;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatTask {
    private String id;
    private String idempotencyKey;
    private String model;
    private String requestJson;
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    // 运行时不需要序列化到 DB 的字段，或者 transient
}

