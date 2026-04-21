package com.kaddycode.internal.provider;

/**
 * AI 프로바이더 추상화 인터페이스
 * 모든 AI 프로바이더는 이 인터페이스를 구현한다.
 */
public interface AiProvider {

    /**
     * 프로바이더 식별자 (ollama / openai / claude / gemini)
     */
    String getProviderName();

    /**
     * 사용 가능 여부 (API 키 설정 여부로 판단)
     */
    boolean isAvailable();

    /**
     * 프롬프트를 받아 AI 응답 반환
     */
    String complete(String prompt, String model);
}