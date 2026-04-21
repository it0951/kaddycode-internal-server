package com.kaddycode.internal.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantVerifier {

    @Value("${backoffice.url:http://localhost:8083}")
    private String backofficeUrl;

    @Value("${backoffice.admin.email:admin@kaddycode.com}")
    private String adminEmail;

    @Value("${backoffice.admin.password:admin1234!}")
    private String adminPassword;

    private final RestTemplate restTemplate;

    // 간단한 인메모리 캐시 (5분 TTL)
    private final ConcurrentHashMap<String, CachedTenant> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    public TenantContext.TenantInfo verify(String email, String apiKey) {
        // 캐시 키: email + apiKey 복합키
        String cacheKey = email + ":" + apiKey;
        CachedTenant cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.info;
        }

        try {
            String adminToken = getAdminToken();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + adminToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> resp = restTemplate.exchange(
                    backofficeUrl + "/api/tenants/verify?apiKey=" + apiKey + "&email=" + email,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Map<String, Object> body = resp.getBody();
                Boolean active = (Boolean) body.get("active");
                if (Boolean.FALSE.equals(active)) {
                    throw new RuntimeException("Tenant is inactive");
                }

                @SuppressWarnings("unchecked")
                Object rawApiKeys = body.get("apiKeys");
                log.debug("[TenantVerifier] rawApiKeys type: {}, value: {}",
                        rawApiKeys != null ? rawApiKeys.getClass().getName() : "null", rawApiKeys);

                List<Map<String, Object>> apiKeys;
                if (rawApiKeys instanceof List<?> list && !list.isEmpty()) {
                    apiKeys = list.stream()
                            .filter(item -> item instanceof Map)
                            .map(item -> (Map<String, Object>) item)
                            .collect(java.util.stream.Collectors.toList());
                } else {
                    apiKeys = java.util.List.of();
                }

                TenantContext.TenantInfo info = new TenantContext.TenantInfo(
                        Long.valueOf(body.get("id").toString()),
                        (String) body.get("name"),
                        (String) body.getOrDefault("aiProvider", "OLLAMA"),
                        (String) body.getOrDefault("aiModel", "qwen2.5-coder:14b"),
                        apiKeys
                );

                cache.put(cacheKey, new CachedTenant(info));
                return info;
            }
        } catch (Exception e) {
            log.error("Failed to verify tenant: {}", e.getMessage());
        }

        throw new RuntimeException("Tenant verification failed");
    }

    private String getAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\"}",
                adminEmail, adminPassword
        );
        ResponseEntity<Map> resp = restTemplate.exchange(
                backofficeUrl + "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );
        return (String) resp.getBody().get("token");
    }

    private record CachedTenant(TenantContext.TenantInfo info, long createdAt) {
        CachedTenant(TenantContext.TenantInfo info) {
            this(info, System.currentTimeMillis());
        }
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }
}