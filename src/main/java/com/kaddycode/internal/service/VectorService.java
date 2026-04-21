package com.kaddycode.internal.service;

import com.kaddycode.internal.dto.request.VectorIndexRequest;
import com.kaddycode.internal.dto.request.VectorSearchRequest;
import com.kaddycode.internal.dto.response.VectorSearchResponse;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import com.kaddycode.internal.domain.entity.QdrantPayloadBackup;
import com.kaddycode.internal.repository.QdrantPayloadBackupRepository;
import com.kaddycode.internal.config.TenantContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorService {

    private final QdrantClient qdrantClient;
    private final RestClient.Builder restClientBuilder;
    private final QdrantPayloadBackupRepository qdrantPayloadBackupRepository;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    @Value("${qdrant.rest-url:http://localhost:6333}")
    private String qdrantRestUrl;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:qwen2.5-coder:14b}")
    private String ollamaModel;

    @Value("${qdrant.vector-size:5120}")
    private int vectorSize;

    // ── 컬렉션 초기화 (gRPC — 영문 메타데이터만 처리하므로 인코딩 무관) ───────
    public void initCollection() throws ExecutionException, InterruptedException {
        List<String> collections = qdrantClient.listCollectionsAsync().get();
        if (!collections.contains(collectionName)) {
            qdrantClient.createCollectionAsync(
                    collectionName,
                    VectorParams.newBuilder()
                            .setSize(vectorSize)
                            .setDistance(Distance.Cosine)
                            .build()
            ).get();
            log.info("Qdrant collection created: {} (vector size: {})", collectionName, vectorSize);
        }
    }

    // ── UUID 변환 헬퍼 ───────────────────────────────────────────────────────
    private String toUuid(String id) {
        try {
            UUID.fromString(id);
            return id;
        } catch (IllegalArgumentException e) {
            String generated = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
            log.debug("ID '{}' converted to UUID: {}", id, generated);
            return generated;
        }
    }

    // ── Ollama 임베딩 ────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    public List<Float> getEmbedding(String text) {
        RestClient client = restClientBuilder.baseUrl(ollamaBaseUrl).build();

        Map<String, Object> body = Map.of(
                "model", ollamaModel,
                "input", text
        );

        Map<?, ?> response = client.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<List<Double>> embeddings = (List<List<Double>>) response.get("embeddings");
        return embeddings.get(0).stream()
                .map(Double::floatValue)
                .toList();
    }

    // ── 인덱싱 — Qdrant REST PUT (gRPC payload 한글 깨짐 완전 우회) ──────────
    @SuppressWarnings("unchecked")
    public void indexCode(VectorIndexRequest request) throws ExecutionException, InterruptedException {
        initCollection();

        String pointId  = toUuid(request.getId());
        List<Float> vec = getEmbedding(request.getCode());

        Map<String, Object> payload = new HashMap<>();
        payload.put("originalId",  request.getId());
        payload.put("filePath",    request.getFilePath());
        payload.put("code",        request.getCode());
        payload.put("language",    request.getLanguage()    != null ? request.getLanguage()    : "unknown");
        payload.put("description", request.getDescription() != null ? request.getDescription() : "");

        Map<String, Object> point = Map.of(
                "id",      pointId,
                "vector",  vec,
                "payload", payload
        );

        Map<String, Object> body = Map.of("points", List.of(point));

        RestClient qdrant = restClientBuilder.baseUrl(qdrantRestUrl).build();
        qdrant.put()
                .uri("/collections/" + collectionName + "/points?wait=true")
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        // ── Qdrant payload backup (PostgreSQL 동시 기록) ─────────────────────
        try {
            TenantContext.TenantInfo tenantInfo = TenantContext.get();
            Long tenantId = tenantInfo != null ? tenantInfo.tenantId() : null;

            QdrantPayloadBackup backup = new QdrantPayloadBackup();
            backup.setCollection(collectionName);
            backup.setVectorId(request.getId());   // qa-UUID 또는 원본 ID
            backup.setTenantId(tenantId);
            backup.setPayload(payload);
            qdrantPayloadBackupRepository.save(backup);
            log.debug("Qdrant payload backup saved: vectorId={}", request.getId());
        } catch (Exception e) {
            log.warn("Qdrant payload backup 저장 실패 (무시): {}", e.getMessage());
        }

        log.info("Code indexed via REST: {} (pointId: {})", request.getFilePath(), pointId);
    }

    // ── 검색 — Qdrant REST POST (쿼리 레벨 필터링 적용) ─────────────────────
    @SuppressWarnings("unchecked")
    public List<VectorSearchResponse> searchCode(VectorSearchRequest request)
            throws ExecutionException, InterruptedException {

        List<Float> queryVec = getEmbedding(request.getQuery());

        // ── 쿼리 바디 구성 ───────────────────────────────────────────────────
        Map<String, Object> body = new HashMap<>();
        body.put("vector",       queryVec);
        body.put("limit",        request.getLimit());
        body.put("with_payload", true);
        body.put("with_vector",  false);

        // score 임계값 (Qdrant score_threshold 파라미터)
        if (request.getScoreThreshold() > 0.0f) {
            body.put("score_threshold", request.getScoreThreshold());
        }

        // 언어 / 파일경로 prefix 필터 (Qdrant filter 파라미터)
        List<Map<String, Object>> mustConditions = new ArrayList<>();

        if (request.getLanguage() != null && !request.getLanguage().isBlank()) {
            mustConditions.add(Map.of(
                    "key", "language",
                    "match", Map.of("value", request.getLanguage())
            ));
        }

        if (request.getFilePathPrefix() != null && !request.getFilePathPrefix().isBlank()) {
            mustConditions.add(Map.of(
                    "key", "filePath",
                    "match", Map.of("text", request.getFilePathPrefix())
            ));
        }

        if (!mustConditions.isEmpty()) {
            body.put("filter", Map.of("must", mustConditions));
        }

        log.debug("Qdrant search body: scoreThreshold={}, language={}, filePathPrefix={}",
                request.getScoreThreshold(), request.getLanguage(), request.getFilePathPrefix());

        // ── Qdrant 호출 ──────────────────────────────────────────────────────
        RestClient qdrant = restClientBuilder.baseUrl(qdrantRestUrl).build();
        Map<?, ?> response = qdrant.post()
                .uri("/collections/" + collectionName + "/points/search")
                .contentType(MediaType.APPLICATION_JSON)
                .acceptCharset(StandardCharsets.UTF_8)
                .body(body)
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("result");
        if (results == null) return List.of();

        List<VectorSearchResponse> responses = new ArrayList<>();
        for (Map<String, Object> point : results) {
            Map<String, Object> pl = (Map<String, Object>) point.get("payload");
            String id       = pl != null ? (String) pl.getOrDefault("originalId", point.get("id").toString()) : point.get("id").toString();
            float  score    = ((Number) point.get("score")).floatValue();
            String filePath = pl != null ? (String) pl.getOrDefault("filePath", "") : "";
            String code     = pl != null ? (String) pl.getOrDefault("code",     "") : "";
            String language = pl != null ? (String) pl.getOrDefault("language", "") : "";
            responses.add(new VectorSearchResponse(id, score, filePath, code, language));
        }

        log.info("Search '{}': {} results (threshold: {})",
                request.getQuery(), responses.size(), request.getScoreThreshold());
        return responses;
    }
}
