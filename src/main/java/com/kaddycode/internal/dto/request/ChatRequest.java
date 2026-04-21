package com.kaddycode.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChatRequest {

    @NotBlank
    private String userId;

    @NotBlank
    private String message;

    // AI 프로바이더 (ollama / openai / claude / gemini), 기본값 ollama
    private String provider = "ollama";

    // 모델명 (미입력 시 각 프로바이더 기본 모델 사용)
    private String model;

    // RAG 사용 여부 (기본 true)
    private boolean useRag = true;

    // RAG 검색 결과 수 (기본 3)
    private int ragLimit = 3;

    // RAG score 임계값 (기본 0.5 이상만 컨텍스트로 사용)
    private float ragScoreThreshold = 0.5f;

    // 캐시 무시 여부 (true 시 STEP1, STEP2 캐시 조회 스킵)
    private boolean bypassCache = false;
}