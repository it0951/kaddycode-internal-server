package com.kaddycode.internal.service;

import com.kaddycode.internal.domain.entity.ChatHistory;
import com.kaddycode.internal.dto.request.VectorIndexRequest;
import com.kaddycode.internal.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetrySaveService {

    private final VectorService vectorService;
    private final ChatHistoryRepository chatHistoryRepository;

    @Retryable(
            retryFor = Exception.class,
            maxAttempts = 2,
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public void saveWithRetry(ChatHistory history, VectorIndexRequest qaIndex) throws Exception {
        // 1. Qdrant QA 캐시 벡터 저장
        if (qaIndex != null) {
            try {
                vectorService.indexCode(qaIndex);
                history.setQuestionVectorId(qaIndex.getId());
                log.info("QA 벡터 저장 완료: vectorId={}", qaIndex.getId());
            } catch (Exception e) {
                log.warn("QA 벡터 저장 실패 (재시도 대상): {}", e.getMessage());
                throw e; // @Retryable 트리거
            }
        }

        // 2. RDB chat_history 저장
        chatHistoryRepository.save(history);
        log.info("chat_history 저장 완료: userId={}, elapsedMs={}ms",
                history.getUserId(), history.getElapsedMs());
    }

    @Recover
    public void saveRecover(Exception e, ChatHistory history, VectorIndexRequest qaIndex) throws Exception {
        log.error("저장 최종 실패 (2회 재시도 후 포기) - userId={}, question={}, error={}",
                history.getUserId(),
                history.getUserMessage(),
                e.getMessage());
    }
}