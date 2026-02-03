package com.mooncell.gateway.api;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

// 这是一个 DTO，实际应放在独立的 api 模块中供 client 引用
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiRequest {
    private String model;
    private Object messages; // List<Message>
    private Double temperature;
    private Boolean stream;
    // ... 其他字段
    
    // 扩展字段，用于回调或标识
    private String requestId;
    // 幂等键
    private String idempotencyKey;
}

