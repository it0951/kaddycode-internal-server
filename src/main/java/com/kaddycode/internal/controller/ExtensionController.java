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

    @Value("${app.vsix-filename}")
    private String vsixFilename;

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
        Resource resource = new FileSystemResource(
                Paths.get(vsixDir, vsixFilename)
        );
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + vsixFilename + "\"")
                .body(resource);
    }
}