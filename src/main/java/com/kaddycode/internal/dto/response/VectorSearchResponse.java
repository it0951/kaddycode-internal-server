package com.kaddycode.internal.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class VectorSearchResponse {
    private String id;
    private float score;
    private String filePath;
    private String code;
    private String language;
}