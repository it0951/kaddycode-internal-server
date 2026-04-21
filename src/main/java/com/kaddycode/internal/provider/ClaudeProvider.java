package com.kaddycode.internal.provider;

import com.kaddycode.internal.config.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ClaudeProvider implements AiProvider {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    private static final String API_VERSION = "2023-06-01";

    private final RestClient restClient;

    @Value("${ai.claude.api-key:}")
    private String envApiKey;

    public ClaudeProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public String getProviderName() { return "claude"; }

    private String resolveApiKey() {
        TenantContext.TenantInfo info = TenantContext.get();
        if (info != null) {
            String key = info.getApiKeyForProvider("CLAUDE");
            if (key != null && !key.isBlank()) return key;
        }
        return envApiKey;
    }

    @Override
    public boolean isAvailable() {
        String key = resolveApiKey();
        return key != null && !key.isBlank();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String prompt, String model) {
        String targetModel = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
        String key = resolveApiKey();

        Map<String, Object> body = Map.of(
                "model", targetModel,
                "max_tokens", 4096,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<?, ?> response = restClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .header("x-api-key", key)
                .header("anthropic-version", API_VERSION)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }
}