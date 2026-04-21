package com.kaddycode.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class VectorSearchRequest {

    @NotBlank
    private String query;

    // 반환할 최대 결과 수
    private int limit = 5;

    // score 임계값 (이 값 미만은 제외, 0.0 = 필터 없음)
    private float scoreThreshold = 0.0f;

    // 언어 필터 (null = 전체)
    private String language;

    // 파일 경로 prefix 필터 (null = 전체), ex) "src/main"
    private String filePathPrefix;
}
