package com.kaddycode.internal.config;

import java.util.List;
import java.util.Map;

public class TenantContext {

    private static final ThreadLocal<TenantInfo> current = new ThreadLocal<>();

    public static void set(TenantInfo info) { current.set(info); }
    public static TenantInfo get() { return current.get(); }
    public static void clear() { current.remove(); }

    public record TenantInfo(
            Long tenantId,
            String tenantName,
            String aiProvider,
            String aiModel,
            List<Map<String, Object>> apiKeys  // ← 추가
    ) {
        // 하위 호환: apiKeys 없는 생성자
        public TenantInfo(Long tenantId, String tenantName,
                          String aiProvider, String aiModel) {
            this(tenantId, tenantName, aiProvider, aiModel, List.of());
        }

        // Provider의 API Key 조회
        public String getApiKeyForProvider(String provider) {
            if (apiKeys == null) return null;
            return apiKeys.stream()
                    .filter(k -> provider.equalsIgnoreCase((String) k.get("provider")))
                    .filter(k -> Boolean.TRUE.equals(k.get("active")))
                    .map(k -> (String) k.get("apiKey"))
                    .findFirst()
                    .orElse(null);
        }

        // Provider의 API URL 조회 (Ollama endpoint 등)
        public String getApiUrlForProvider(String provider) {
            if (apiKeys == null) return null;
            return apiKeys.stream()
                    .filter(k -> provider.equalsIgnoreCase((String) k.get("provider")))
                    .filter(k -> Boolean.TRUE.equals(k.get("active")))
                    .map(k -> (String) k.get("apiUrl"))
                    .findFirst()
                    .orElse(null);
        }

        // 활성 Provider 목록 (중복 제거)
        public List<String> getActiveProviders() {
            if (apiKeys == null) return List.of();
            return apiKeys.stream()
                    .filter(k -> Boolean.TRUE.equals(k.get("active")))
                    .map(k -> (String) k.get("provider"))
                    .distinct()   // ← 이것만 추가
                    .toList();
        }
    }
}