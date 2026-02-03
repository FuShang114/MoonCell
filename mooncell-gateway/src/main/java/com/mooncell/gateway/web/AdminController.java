package com.mooncell.gateway.web;

import com.mooncell.gateway.core.cache.ModelCacheService;
import com.mooncell.gateway.core.dao.ModelInstanceMapper;
import com.mooncell.gateway.core.model.ModelInstance;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ModelInstanceMapper mapper;
    private final ModelCacheService cacheService;

    // Nacos-like 监控接口：获取所有服务状态
    @GetMapping("/monitor")
    public Map<String, List<ModelInstance>> getMonitorData() {
        // 从缓存中直接获取，包含实时的 failureCount 等状态
        return cacheService.getAllCached();
    }

    // 注册新服务节点 (持久化 + 刷新缓存)
    @PostMapping("/instances")
    public String addInstance(@RequestBody AddInstanceRequest request) throws Exception {
        // 1. 获取 providerId
        Long providerId = mapper.findProviderIdByName(request.getProvider());
        if (providerId == null) {
            log.error("非法服务商:{}大模型：{}", request.getProvider(), request.getModel());
            throw new Exception("非法服务商");
        }

        // 2. 插入 DB
        ModelInstance instance = ModelInstance.builder()
                .providerId(providerId)
                .modelName(request.getModel())
                .url(request.getUrl())
                .apiKey(request.getApiKey())
                .weight(10)
                .isActive(true)
                .build();
        
        mapper.insert(instance);

        // 3. 触发缓存刷新 (Copy-On-Write 效果)
        cacheService.refresh(request.getModel());
        
        return "Instance added and cache refreshed for model: " + request.getModel();
    }

    @Data
    public static class AddInstanceRequest {
        private String model;
        private String url;    // 核心标识
        private String apiKey;
        private String provider;
    }
}
