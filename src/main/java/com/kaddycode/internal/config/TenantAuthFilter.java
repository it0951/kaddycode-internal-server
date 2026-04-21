package com.kaddycode.internal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantAuthFilter extends OncePerRequestFilter {

    @Value("${backoffice.url:http://localhost:8083}")
    private String backofficeUrl;

    @Value("${tenant.auth.enabled:true}")
    private boolean authEnabled;

    private final TenantVerifier tenantVerifier;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/health")
                || path.equals("/api/v1/health")
                || path.equals("/api/v1/tenant/verify")
                || path.startsWith("/actuator")
                || path.startsWith("/api/extension");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!authEnabled) {
            // 개발 편의용: 인증 비활성화 시 기본 테넌트로 진행
            TenantContext.set(new TenantContext.TenantInfo(
                    0L, "default", "OLLAMA", "qwen2.5-coder:14b"
            ));
            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
            return;
        }

        String apiKey = request.getHeader("X-Tenant-API-Key");
        String email  = request.getHeader("X-Tenant-Email");   // ← 추가

        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"X-Tenant-API-Key header is required\"}");
            return;
        }

        if (email == null || email.isBlank()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"X-Tenant-Email header is required\"}");
            return;
        }

        try {
            TenantContext.TenantInfo tenantInfo = tenantVerifier.verify(email, apiKey);
            TenantContext.set(tenantInfo);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("Tenant auth failed for key: {}***", apiKey.substring(0, Math.min(8, apiKey.length())));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or inactive tenant API key\"}");
        } finally {
            TenantContext.clear();
        }
    }
}