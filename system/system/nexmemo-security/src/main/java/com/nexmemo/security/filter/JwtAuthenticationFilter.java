package com.nexmemo.security.filter;

import com.nexmemo.security.service.TokenBlacklistService;
import com.nexmemo.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter.
 *
 * Resolves the JWT from the Authorization: Bearer header ONLY.
 * Cookie-based token resolution has been removed because:
 * 1. Cookies are vulnerable to CSRF when Bearer tokens are used
 * 2. The frontend always sends the Authorization header via the HttpInterceptor
 * 3. SameSite=Strict cookies are set for potential future use but are never
 *    consumed by this filter — eliminating the CSRF attack surface entirely
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String token = resolveToken(req);

        if (StringUtils.hasText(token) && jwtUtil.isTokenValid(token)) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                String jti = claims.getId();
                String tokenType = claims.get("type", String.class);

                if ("ACCESS".equals(tokenType) && blacklistService.isAccessTokenBlacklisted(jti)) {
                    log.warn("Blacklisted access token used: jti={}", jti);
                    SecurityContextHolder.clearContext();
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Token has been revoked\"}");
                    return;
                }

                String role = claims.get("role", String.class);
                Long userId = Long.parseLong(claims.getSubject());
                String username = claims.get("username", String.class);

                var auth = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );
                auth.setDetails(username);
                SecurityContextHolder.getContext().setAuthentication(auth);

            } catch (Exception e) {
                log.warn("JWT processing error: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(req, res);
    }

    /**
     * Resolves JWT from Authorization: Bearer header only.
     * Cookie-based resolution has been removed for CSRF mitigation.
     */
    private String resolveToken(HttpServletRequest req) {
        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
