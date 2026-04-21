package com.kaddycode.internal.controller;

import com.kaddycode.internal.provider.AiProviderFactory;
import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final QdrantClient qdrantClient;
    private final AiProviderFactory aiProviderFactory;

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("server", "UP");
        status.put("database", "UP");

        try {
            qdrantClient.listCollectionsAsync().get();
            status.put("qdrant", "UP");
        } catch (InterruptedException | ExecutionException e) {
            status.put("qdrant", "DOWN");
            status.put("qdrant_error", e.getMessage());
        }

        // 사용 가능한 AI 프로바이더 목록
        status.put("availableProviders", aiProviderFactory.getAvailableProviders());

        return status;
    }
}