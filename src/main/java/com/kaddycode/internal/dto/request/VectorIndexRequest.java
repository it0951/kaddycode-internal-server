package com.kaddycode.internal.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class VectorIndexRequest {

    @NotBlank
    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String filePath;

    private String language;
    private String description;
}