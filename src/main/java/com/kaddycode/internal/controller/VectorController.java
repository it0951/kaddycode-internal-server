package com.kaddycode.internal.controller;

import com.kaddycode.internal.dto.request.VectorIndexRequest;
import com.kaddycode.internal.dto.request.VectorSearchRequest;
import com.kaddycode.internal.dto.response.VectorSearchResponse;
import com.kaddycode.internal.service.VectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/vector")
@RequiredArgsConstructor
public class VectorController {

    private final VectorService vectorService;

    @PostMapping("/index")
    public ResponseEntity<Map<String, String>> indexCode(
            @Valid @RequestBody VectorIndexRequest request)
            throws ExecutionException, InterruptedException {
        vectorService.indexCode(request);
        return ResponseEntity.ok(Map.of("status", "indexed", "id", request.getId()));
    }

    @PostMapping("/search")
    public ResponseEntity<List<VectorSearchResponse>> searchCode(
            @Valid @RequestBody VectorSearchRequest request)
            throws ExecutionException, InterruptedException {
        return ResponseEntity.ok(vectorService.searchCode(request));
    }
}