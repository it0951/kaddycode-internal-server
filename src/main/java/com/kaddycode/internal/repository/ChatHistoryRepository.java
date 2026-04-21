package com.kaddycode.internal.repository;

import com.kaddycode.internal.domain.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    List<ChatHistory> findByUserIdOrderByCreatedAtDesc(String userId);

    // 테넌트 + 동일 질문 정확 매칭 (캐시 히트용)
    @Query("SELECT c FROM ChatHistory c " +
            "WHERE c.tenantId = :tenantId " +
            "AND LOWER(c.userMessage) = LOWER(:message) " +
            "AND c.assistantMessage IS NOT NULL " +
            "ORDER BY c.createdAt DESC")
    List<ChatHistory> findExactMatch(
            @Param("tenantId") Long tenantId,
            @Param("message") String message);

    // 테넌트별 전체 히스토리
    List<ChatHistory> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    // 캐시 히트 통계 (Backoffice 대시보드용)
    long countByTenantIdAndCacheHitTrue(Long tenantId);
    long countByTenantId(Long tenantId);

    // ── Backoffice 조회 필터 ──────────────────────────────────────────────

    // 1. 오래 걸린 것 (응답시간 상위 N개)
    @Query("SELECT c FROM ChatHistory c " +
            "WHERE c.tenantId = :tenantId " +
            "AND c.responseTimeMs IS NOT NULL " +
            "AND c.error = false " +
            "ORDER BY c.responseTimeMs DESC")
    List<ChatHistory> findSlowQueries(
            @Param("tenantId") Long tenantId,
            org.springframework.data.domain.Pageable pageable);

    // 2. 자주 조회된 것 (캐시 히트된 질문 = 반복 질문)
    @Query("SELECT c FROM ChatHistory c " +
            "WHERE c.tenantId = :tenantId " +
            "AND c.cacheHit = true " +
            "ORDER BY c.createdAt DESC")
    List<ChatHistory> findCacheHitQueries(
            @Param("tenantId") Long tenantId,
            org.springframework.data.domain.Pageable pageable);

    // 3. 오류 발생한 것
    @Query("SELECT c FROM ChatHistory c " +
            "WHERE c.tenantId = :tenantId " +
            "AND c.error = true " +
            "ORDER BY c.createdAt DESC")
    List<ChatHistory> findErrorQueries(
            @Param("tenantId") Long tenantId,
            org.springframework.data.domain.Pageable pageable);

    // 4. 큰 토큰 사용한 것 (토큰 상위 N개)
    @Query("SELECT c FROM ChatHistory c " +
            "WHERE c.tenantId = :tenantId " +
            "AND c.tokenCount IS NOT NULL " +
            "ORDER BY c.tokenCount DESC")
    List<ChatHistory> findHighTokenQueries(
            @Param("tenantId") Long tenantId,
            org.springframework.data.domain.Pageable pageable);

    // 5. 전체 조회 (페이징)
    @Query("SELECT c FROM ChatHistory c " +
            "WHERE c.tenantId = :tenantId " +
            "ORDER BY c.createdAt DESC")
    List<ChatHistory> findByTenantIdPaged(
            @Param("tenantId") Long tenantId,
            org.springframework.data.domain.Pageable pageable);
}