package com.kaddycode.internal.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_history", indexes = {
        @Index(name = "idx_chat_tenant_user", columnList = "tenantId, userId"),
        @Index(name = "idx_chat_created", columnList = "createdAt")
})
@Getter @Setter
@NoArgsConstructor
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId;

    @Column
    private Long tenantId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String userMessage;

    @Column(columnDefinition = "TEXT")
    private String assistantMessage;

    @Column(nullable = false)
    private String model;

    @Column
    private String questionVectorId;

    @Column(nullable = false)
    private boolean cacheHit = false;

    @Column
    private Long responseTimeMs;

    @Column
    private Integer tokenCount;

    @Column(nullable = false)
    private boolean error = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    // ── 26일차 추가 ──────────────────────────────────────────────────────────

    // 캐시 히트 시 매칭된 원본 질문
    @Column(length = 2000)
    private String cachedQuestion;

    // 응답 출처: QA_CACHE / AI_DIRECT
    @Column(length = 20)
    private String responseSource;

    // AI 프로바이더명 (OLLAMA / OPENAI / CLAUDE / GEMINI)
    @Column(length = 50)
    private String provider;

    // AI 호출 실제 소요시간 (ms), 캐시 히트 시 0
    @Column
    private Integer elapsedMs;

    // ────────────────────────────────────────────────────────────────────────

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}