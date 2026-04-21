package com.kaddycode.internal.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/extension")
public class ExtensionController {

    @Value("${app.version}")
    private String appVersion;

    @Value("${app.vsix-dir}")
    private String vsixDir;

    /** Extension 설정창 버전 확인용 */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> getVersion() {
        return ResponseEntity.ok(Map.of("version", appVersion));
    }

    /** /verify 페이지 및 Extension에서 VSIX 다운로드 */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadVsix() {
        // vsix-dir에서 .vsix 파일 동적 탐색
        java.io.File dir = new java.io.File(vsixDir);
        java.io.File[] vsixFiles = dir.listFiles((d, name) -> name.endsWith(".vsix"));
        if (vsixFiles == null || vsixFiles.length == 0) {
            return ResponseEntity.notFound().build();
        }
        // 가장 최근 수정된 파일 선택
        java.util.Arrays.sort(vsixFiles, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        java.io.File vsixFile = vsixFiles[0];

        Resource resource = new FileSystemResource(vsixFile);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + vsixFile.getName() + "\"")
                .body(resource);
    }
}