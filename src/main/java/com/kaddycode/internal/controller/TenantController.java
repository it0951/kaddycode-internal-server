package com.kaddycode.internal.controller;

import com.kaddycode.internal.config.TenantContext;
import com.kaddycode.internal.config.TenantVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantVerifier tenantVerifier;

    @PostMapping("/verify")
    public ResponseEntity<?> verify(
            @RequestHeader(value = "X-Tenant-API-Key", required = false) String apiKey,
            @RequestHeader(value = "X-Tenant-Email", required = false) String email) {

        if (apiKey == null || apiKey.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "message", "API Key가 없습니다."));
        }
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("valid", false, "message", "이메일이 없습니다."));
        }

        try {
            TenantContext.TenantInfo info = tenantVerifier.verify(email, apiKey);
            return ResponseEntity.ok(Map.of(
                    "active",          true,
                    "tenantId",        info.tenantId(),
                    "name",            info.tenantName(),
                    "aiProvider",      info.aiProvider(),
                    "aiModel",         info.aiModel(),
                    "apiKeys",         info.apiKeys(),
                    "activeProviders", info.getActiveProviders()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(Map.of("valid", false, "message", e.getMessage()));
        }
    }
}