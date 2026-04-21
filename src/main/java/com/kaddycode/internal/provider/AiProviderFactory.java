package com.kaddycode.internal.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 프로바이더 팩토리
 * provider 이름으로 적절한 AiProvider 구현체를 반환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiProviderFactory {

    private final List<AiProvider> providers;

    private Map<String, AiProvider> providerMap() {
        return providers.stream()
                .collect(Collectors.toMap(AiProvider::getProviderName, Function.identity()));
    }

    /**
     * provider 이름으로 구현체 조회
     * 없거나 비활성화된 경우 ollama 폴백
     */
    public AiProvider getProvider(String providerName) {
        Map<String, AiProvider> map = providerMap();

        if (providerName != null && !providerName.isBlank()) {
            AiProvider provider = map.get(providerName.toLowerCase());
            if (provider != null && provider.isAvailable()) {
                log.debug("AI provider selected: {}", providerName);
                return provider;
            }
            log.warn("Provider '{}' not available, falling back to ollama", providerName);
        }

        // 기본 폴백: ollama
        AiProvider fallback = map.get("ollama");
        if (fallback == null) {
            throw new IllegalStateException("No AI provider available");
        }
        return fallback;
    }

    /**
     * 사용 가능한 프로바이더 목록 반환
     */
    public List<String> getAvailableProviders() {
        return providers.stream()
                .filter(AiProvider::isAvailable)
                .map(AiProvider::getProviderName)
                .collect(Collectors.toList());
    }
}