package com.kaddycode.internal.provider;

import com.kaddycode.internal.config.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Component
public class OllamaProvider implements AiProvider {

    private final RestClient.Builder restClientBuilder;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String envBaseUrl;

    @Value("${ollama.model:qwen2.5-coder:7b}")
    private String defaultModel;

    public OllamaProvider(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public String getProviderName() { return "ollama"; }

    private String resolveBaseUrl() {
        TenantContext.TenantInfo info = TenantContext.get();
        if (info != null) {
            String url = info.getApiUrlForProvider("OLLAMA");
            if (url != null && !url.isBlank()) return url;
        }
        return envBaseUrl;
    }

    @Override
    public boolean isAvailable() {
        try {
            restClientBuilder.baseUrl(resolveBaseUrl()).build()
                    .get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String prompt, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : defaultModel;
        String baseUrl = resolveBaseUrl();

        // Map.of() → HashMap으로 변경 (system 필드 추가)
        Map<String, Object> body = new HashMap<>();
        body.put("model", targetModel);
        body.put("system",
                "You are a helpful coding assistant specialized in enterprise software development. " +
                        "IMPORTANT: Always respond in the same language as the user's input. " +
                        "If the user writes in Korean, you MUST respond in Korean. " +
                        "If the user writes in English, you MUST respond in English. " +
                        "Never respond in Chinese under any circumstances, " +
                        "regardless of your training language or model origin.");
        body.put("prompt", prompt);
        body.put("stream", false);

        Map<?, ?> response = restClientBuilder.baseUrl(baseUrl).build().post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .body(body)
                .retrieve()
                .body(Map.class);

        return (String) response.get("response");
    }
}