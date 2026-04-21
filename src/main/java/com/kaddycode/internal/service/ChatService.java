package com.kaddycode.internal.service;

import com.kaddycode.internal.config.TenantContext;
import com.kaddycode.internal.domain.entity.ChatHistory;
import com.kaddycode.internal.dto.request.ChatRequest;
import com.kaddycode.internal.dto.request.VectorIndexRequest;
import com.kaddycode.internal.dto.request.VectorSearchRequest;
import com.kaddycode.internal.dto.response.ChatResponse;
import com.kaddycode.internal.dto.response.VectorSearchResponse;
import com.kaddycode.internal.provider.AiProvider;
import com.kaddycode.internal.provider.AiProviderFactory;
import com.kaddycode.internal.repository.ChatHistoryRepository;
import com.kaddycode.internal.repository.QdrantPayloadBackupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final VectorService vectorService;
    private final AiProviderFactory aiProviderFactory;
    private final AsyncSaveService asyncSaveService;
    private final QdrantPayloadBackupRepository qdrantPayloadBackupRepository;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    private static final double QA_CACHE_THRESHOLD       = 0.90;  // 0.95 → 0.90 완화
    private static final double QA_LENGTH_RATIO_THRESHOLD = 0.7;
    private static final double QA_KEYWORD_MATCH_THRESHOLD = 0.5;  // 신규: 키워드 매칭률
    private static final int    QA_MIN_VECTOR_COUNT       = 5;     // 신규: 최소 QA 벡터 수

    public ChatResponse chat(ChatRequest request) {

        TenantContext.TenantInfo tenantInfo = TenantContext.get();
        Long tenantId = tenantInfo != null ? tenantInfo.tenantId() : null;

        // ── STEP 1: 정확 매칭 캐시 조회 ─────────────────────────────────────
        if (tenantId != null && !request.isBypassCache()) {
            List<ChatHistory> exactMatches = chatHistoryRepository
                    .findExactMatch(tenantId, request.getMessage());

            if (!exactMatches.isEmpty()) {
                ChatHistory cached = exactMatches.get(0);
                log.info("Cache HIT (exact): tenantId={}, question={}", tenantId, request.getMessage());

                ChatHistory hitHistory = new ChatHistory();
                hitHistory.setUserId(request.getUserId());
                hitHistory.setTenantId(tenantId);
                hitHistory.setUserMessage(request.getMessage());
                hitHistory.setAssistantMessage(cached.getAssistantMessage());
                hitHistory.setModel(cached.getModel() + " [캐시]");
                hitHistory.setCacheHit(true);
                hitHistory.setCachedQuestion(cached.getUserMessage());
                hitHistory.setResponseSource("QA_CACHE");
                hitHistory.setProvider(request.getProvider());
                hitHistory.setElapsedMs(0);
                chatHistoryRepository.save(hitHistory);

                return new ChatResponse(
                        request.getUserId(),
                        cached.getAssistantMessage(),
                        cached.getModel() + " [캐시]",
                        cached.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        false,
                        List.of()
                );
            }
        }

        // ── STEP 2: 유사 질문 벡터 캐시 조회 (형태소+키워드 고도화) ──────────
        if (tenantId != null && !request.isBypassCache()) {
            try {
                // 최소 QA 벡터 수 확인 (데이터 부족 시 캐시 스킵)
                long qaVectorCount = qdrantPayloadBackupRepository
                        .countQaVectorsByTenantId(collectionName, tenantId);

                if (qaVectorCount < QA_MIN_VECTOR_COUNT) {
                    log.info("QA cache SKIP: 최소 벡터 수 미달 ({}/{})", qaVectorCount, QA_MIN_VECTOR_COUNT);
                } else {
                    VectorSearchRequest qaSearch = new VectorSearchRequest();
                    qaSearch.setQuery(request.getMessage());
                    qaSearch.setLimit(3);  // 후보 3개로 확대
                    qaSearch.setFilePathPrefix("qa://" + tenantId + "/");

                    List<VectorSearchResponse> qaCandidates = vectorService.searchCode(qaSearch);

                    if (!qaCandidates.isEmpty()) {
                        // 벡터 점수(60%) + 키워드 매칭률(40%) 가중 합산으로 최적 후보 선택
                        VectorSearchResponse best = qaCandidates.stream()
                                .max(Comparator.comparingDouble(c -> {
                                    double kw = calcKeywordMatchRatio(request.getMessage(), c.getCode());
                                    return c.getScore() * 0.6 + kw * 0.4;
                                }))
                                .orElse(qaCandidates.get(0));

                        String cachedQ    = best.getCode();
                        String currentQ   = request.getMessage();
                        double lengthRatio  = (double) Math.min(cachedQ.length(), currentQ.length())
                                / Math.max(cachedQ.length(), currentQ.length());
                        double keywordRatio = calcKeywordMatchRatio(currentQ, cachedQ);

                        log.info("QA vector candidate: score={}, lengthRatio={}, keywordRatio={}, cachedQ='{}', currentQ='{}'",
                                String.format("%.4f", best.getScore()),
                                String.format("%.3f", lengthRatio),
                                String.format("%.3f", keywordRatio),
                                cachedQ, currentQ);

                        boolean scoreOk   = best.getScore()  >= QA_CACHE_THRESHOLD;
                        boolean lengthOk  = lengthRatio       >= QA_LENGTH_RATIO_THRESHOLD;
                        boolean keywordOk = keywordRatio       >= QA_KEYWORD_MATCH_THRESHOLD;

                        // 벡터 점수 OK + (길이 OR 키워드) 중 하나 만족
                        if (scoreOk && (lengthOk || keywordOk)) {
                            List<ChatHistory> matched = chatHistoryRepository
                                    .findExactMatch(tenantId, cachedQ);

                            if (!matched.isEmpty()) {
                                ChatHistory cached = matched.get(0);
                                log.info("Cache HIT (vector+keyword): score={}, lengthRatio={}, keywordRatio={}, question={}",
                                        String.format("%.4f", best.getScore()),
                                        String.format("%.3f", lengthRatio),
                                        String.format("%.3f", keywordRatio),
                                        request.getMessage());

                                ChatHistory hitHistory = new ChatHistory();
                                hitHistory.setUserId(request.getUserId());
                                hitHistory.setTenantId(tenantId);
                                hitHistory.setUserMessage(request.getMessage());
                                hitHistory.setAssistantMessage(cached.getAssistantMessage());
                                hitHistory.setModel(cached.getModel() + " [유사질문 캐시]");
                                hitHistory.setCacheHit(true);
                                hitHistory.setCachedQuestion(cached.getUserMessage());
                                hitHistory.setResponseSource("QA_CACHE");
                                hitHistory.setProvider(request.getProvider());
                                hitHistory.setElapsedMs(0);
                                chatHistoryRepository.save(hitHistory);

                                return new ChatResponse(
                                        request.getUserId(),
                                        cached.getAssistantMessage(),
                                        cached.getModel() + " [유사질문 캐시]",
                                        cached.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                                        false,
                                        List.of()
                                );
                            }
                        } else {
                            log.info("Cache MISS (vector+keyword): score={} ok={}, lengthRatio={} ok={}, keywordRatio={} ok={}",
                                    String.format("%.4f", best.getScore()), scoreOk,
                                    String.format("%.3f", lengthRatio), lengthOk,
                                    String.format("%.3f", keywordRatio), keywordOk);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("QA vector cache search failed: {}", e.getMessage());
            }
        }

        // ── STEP 3: 프로바이더 선택 ──────────────────────────────────────────
        AiProvider provider = aiProviderFactory.getProvider(request.getProvider());
        log.info("Cache MISS → AI 호출: provider={}, model={}", provider.getProviderName(), request.getModel());

        // ── STEP 4: RAG 코드 검색 ────────────────────────────────────────────
        List<VectorSearchResponse> ragResults = new ArrayList<>();
        boolean ragUsed = false;

        if (request.isUseRag()) {
            try {
                VectorSearchRequest searchReq = new VectorSearchRequest();
                searchReq.setQuery(request.getMessage());
                searchReq.setLimit(request.getRagLimit());

                List<VectorSearchResponse> candidates = vectorService.searchCode(searchReq);

                ragResults = candidates.stream()
                        .filter(r -> r.getScore() >= request.getRagScoreThreshold())
                        .filter(r -> !r.getFilePath().startsWith("qa://"))
                        .collect(Collectors.toList());

                ragUsed = !ragResults.isEmpty();
                log.info("RAG: {} candidates, {} passed threshold", candidates.size(), ragResults.size());
            } catch (Exception e) {
                log.warn("RAG search failed: {}", e.getMessage());
            }
        }

        // ── STEP 5: 프롬프트 구성 + AI 호출 (응답시간 측정) ─────────────────
        String prompt = buildPrompt(request.getMessage(), ragResults);
        String modelName = provider.getProviderName()
                + (request.getModel() != null ? "/" + request.getModel() : "");

        long startTime = System.currentTimeMillis();
        String assistantMessage;
        boolean isError = false;
        String errorMessage = null;

        try {
            assistantMessage = provider.complete(prompt, request.getModel());
        } catch (Exception e) {
            isError = true;
            errorMessage = e.getMessage();
            assistantMessage = "오류가 발생했습니다: " + e.getMessage();
            log.error("AI 호출 오류: provider={}, error={}", provider.getProviderName(), e.getMessage());
        }

        long responseTimeMs = System.currentTimeMillis() - startTime;
        int tokenCount = (request.getMessage().length() + assistantMessage.length()) / 4;

        // ── STEP 6: history 구성 ─────────────────────────────────────────────
        ChatHistory history = new ChatHistory();
        history.setUserId(request.getUserId());
        history.setTenantId(tenantId);
        history.setUserMessage(request.getMessage());
        history.setAssistantMessage(assistantMessage);
        history.setModel(modelName);
        history.setCacheHit(false);
        history.setResponseTimeMs(responseTimeMs);
        history.setTokenCount(tokenCount);
        history.setError(isError);
        history.setErrorMessage(errorMessage);
        history.setResponseSource("AI_DIRECT");
        history.setProvider(request.getProvider());
        history.setElapsedMs((int) responseTimeMs);

        // ── STEP 7: QA 벡터 인덱스 요청 객체 구성 ───────────────────────────
        VectorIndexRequest qaIndex = null;
        if (tenantId != null && !isError) {
            String vectorId = "qa-" + UUID.randomUUID();
            qaIndex = new VectorIndexRequest();
            qaIndex.setId(vectorId);
            qaIndex.setCode(request.getMessage());
            qaIndex.setFilePath("qa://" + tenantId + "/" + vectorId);
            qaIndex.setLanguage("qa");
            qaIndex.setDescription(assistantMessage.substring(
                    0, Math.min(200, assistantMessage.length())));
        }

        // ── STEP 8: 비동기 저장 ──────────────────────────────────────────────
        if (!isError) {
            asyncSaveService.saveAsync(history, qaIndex);
            log.info("비동기 저장 요청: userId={}, elapsedMs={}ms", request.getUserId(), responseTimeMs);
        }

        if (!isError) {
            chatHistoryRepository.save(history);
            log.info("응답 완료: responseTimeMs={}ms, tokenCount={}", responseTimeMs, tokenCount);
        } else {
            log.warn("AI 오류로 DB 저장 스킵: provider={}, error={}", provider.getProviderName(), errorMessage);
        }

        // ── STEP 9: 참조 파일 목록 반환 ─────────────────────────────────────
        List<String> references = ragResults.stream()
                .map(VectorSearchResponse::getFilePath)
                .distinct()
                .collect(Collectors.toList());

        if (isError) throw new RuntimeException(errorMessage);

        return new ChatResponse(
                request.getUserId(),
                assistantMessage,
                modelName,
                history.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ragUsed,
                references
        );
    }

    // ── 키워드 추출 (규칙 기반 형태소 분석) ─────────────────────────────────
    private Set<String> extractKeywords(String text) {
        String[] koParticles = {"에서", "으로", "이란", "이야", "이다", "인가", "인지",
                "이고", "은", "는", "이", "가", "을", "를", "에",
                "의", "로", "과", "와", "도", "만", "고", "다"};
        Set<String> enStopWords = Set.of(
                "is","are","was","were","the","a","an","and","or","but",
                "in","on","at","to","for","of","with","by","from","how",
                "what","when","where","why","who","which","this","that",
                "it","its","be","been","being","have","has","had",
                "do","does","did","will","would","could","should","may","might"
        );

        Set<String> keywords = new HashSet<>();
        String[] tokens = text.toLowerCase()
                .replaceAll("[\\[\\]{}()\"'`<>,.!?;:@#$%^&*+=|\\\\~]", " ")
                .split("\\s+");

        for (String token : tokens) {
            if (token.length() < 2) continue;
            if (token.matches("[a-z0-9_]+") && enStopWords.contains(token)) continue;

            String cleaned = token;
            for (String particle : koParticles) {
                if (cleaned.endsWith(particle) && cleaned.length() > particle.length() + 1) {
                    cleaned = cleaned.substring(0, cleaned.length() - particle.length());
                    break;
                }
            }
            if (cleaned.length() >= 2) keywords.add(cleaned);
        }
        return keywords;
    }

    // ── 키워드 매칭률: 공통 키워드 / 질문 키워드 ────────────────────────────
    private double calcKeywordMatchRatio(String query, String cached) {
        Set<String> queryKw  = extractKeywords(query);
        Set<String> cachedKw = extractKeywords(cached);
        if (queryKw.isEmpty()) return 0.0;
        long common = queryKw.stream().filter(cachedKw::contains).count();
        return (double) common / queryKw.size();
    }

    private String buildPrompt(String userMessage, List<VectorSearchResponse> ragResults) {
        if (ragResults.isEmpty()) return userMessage;
        StringBuilder sb = new StringBuilder();
        sb.append("아래는 질문과 관련된 코드베이스의 참고 자료입니다.\n\n");
        for (int i = 0; i < ragResults.size(); i++) {
            VectorSearchResponse r = ragResults.get(i);
            sb.append(String.format("### 참고 %d: %s (score: %.3f)\n", i + 1, r.getFilePath(), r.getScore()));
            sb.append("```").append(r.getLanguage()).append("\n");
            sb.append(r.getCode()).append("\n");
            sb.append("```\n\n");
        }
        sb.append("---\n위 참고 자료를 바탕으로 다음 질문에 답변해주세요:\n\n");
        sb.append(userMessage);
        return sb.toString();
    }

    public List<ChatHistory> getHistory(String userId) {
        return chatHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}