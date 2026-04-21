package com.kaddycode.internal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
@AllArgsConstructor
public class ChatResponse {

    private String userId;
    private String message;
    private String model;
    private String createdAt;

    // RAG 컨텍스트 사용 여부
    private boolean ragUsed;

    // 참조된 파일 경로 목록
    private List<String> references;
}
