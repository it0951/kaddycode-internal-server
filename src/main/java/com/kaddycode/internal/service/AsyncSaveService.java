package com.kaddycode.internal.service;

import com.kaddycode.internal.domain.entity.ChatHistory;
import com.kaddycode.internal.dto.request.VectorIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncSaveService {

    private final RetrySaveService retrySaveService;

    @Async
    public void saveAsync(ChatHistory history, VectorIndexRequest qaIndex) {
        log.debug("비동기 저장 시작: userId={}", history.getUserId());
        try {
            retrySaveService.saveWithRetry(history, qaIndex);
        } catch (Exception e) {
            // @Recover 에서 처리되므로 여기까지 오는 경우는 없지만 안전망
            log.error("AsyncSaveService 예외 (saveRecover 미작동): {}", e.getMessage());
        }
    }
}