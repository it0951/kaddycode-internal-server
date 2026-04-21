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
public class OpenAiProvider implements AiProvider {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final RestClient restClient;

    @Value("${ai.openai.api-key:}")
    private String envApiKey;

    public OpenAiProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public String getProviderName() { return "openai"; }

    private String resolveApiKey() {
        TenantContext.TenantInfo info = TenantContext.get();
        if (info != null) {
            String key = info.getApiKeyForProvider("OPENAI");
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
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        Map<?, ?> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .header("Authorization", "Bearer " + key)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}