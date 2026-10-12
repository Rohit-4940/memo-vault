package com.nexmemo.web.config;

import com.nexmemo.web.service.ApiPermissionService;
import com.nexmemo.web.service.AuditEventPublisher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Enforces the role→URL permission matrix ({@code url_api} / {@code role_permission_api}) on every
 * authenticated request, after the Spring Security chain has established the caller's identity.
 *
 * <p>Runs as a servlet filter registered with the default (lowest-precedence) order, so it executes
 * <b>inside</b> the Spring Security chain's scope where the {@code SecurityContext} is populated.
 * Unauthenticated requests (no {@link Long} principal — e.g. login, refresh, WebSocket handshake)
 * are skipped; they are already governed by the security chain.</p>
 *
 * <p>The decision itself lives in {@link ApiPermissionService}, which <b>fails open</b> for roles
 * that have no URL rights configured. This means enabling the filter never locks out users on a
 * deployment where the matrix has not been populated, while still enforcing the matrix the moment
 * an administrator configures it.</p>
 *
 * <p>Disable with {@code nexmemo.security.permission-enforcement.enabled=false}.</p>
 *
 * @author : NexMemo
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiPermissionEnforcementFilter extends OncePerRequestFilter {

    private final ApiPermissionService apiPermissionService;
    private final AuditEventPublisher auditEventPublisher;

    @Value("${nexmemo.security.permission-enforcement.enabled:true}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long userId)) {
            chain.doFilter(request, response);
            return;
        }

        String servletPath = request.getServletPath();
        if (apiPermissionService.hasApiAccess(userId, request.getMethod(), servletPath)) {
            chain.doFilter(request, response);
            return;
        }

        log.warn("Role permission matrix denied userId={} {} {}", userId, request.getMethod(), servletPath);
        auditEventPublisher.accessDenied(userId, "API", null,
                request.getMethod() + " " + servletPath + " denied by role permission matrix");

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied by role permission policy\"}");
    }
}
