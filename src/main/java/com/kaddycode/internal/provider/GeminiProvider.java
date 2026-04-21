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
public class GeminiProvider implements AiProvider {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private final RestClient restClient;

    @Value("${ai.gemini.api-key:}")
    private String envApiKey;

    public GeminiProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
    }

    @Override
    public String getProviderName() { return "gemini"; }

    private String resolveApiKey() {
        TenantContext.TenantInfo info = TenantContext.get();
        if (info != null) {
            String key = info.getApiKeyForProvider("GEMINI");
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
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        Map<?, ?> response = restClient.post()
                .uri("/v1/models/" + targetModel + ":generateContent?key=" + key)
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        return (String) parts.get(0).get("text");
    }
}