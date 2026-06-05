package com.nexmemo.security.config;

import com.nexmemo.core.dto.SecurityProperties;
import com.nexmemo.security.filter.JwtAuthenticationFilter;
import com.nexmemo.security.filter.RateLimitFilter;
import com.nexmemo.security.filter.SecurityHeadersFilter;
import com.nexmemo.security.service.TokenBlacklistService;
import com.nexmemo.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final SecurityProperties securityProperties;
    private final NexmemoCorsProperties corsProperties;
    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    @Value("${nexmemo.security.rate-limit.redis-enabled:false}")
    private boolean redisEnabled;

    /**
     * PUBLIC_ENDPOINTS are endpoints that require NO authentication.
     *
     * Security principle: every endpoint should require authentication unless
     * there is an explicit reason not to. For production:
     * - Swagger (/v3/api-docs, /swagger-ui) is REMOVED — API docs leak attack surface.
     *   Enable them in dev only via a separate profile.
     * - Actuator health is REMOVED — use a dedicated monitoring endpoint instead
     *   or restrict to internal network via a separate filter.
     * - WebSocket (/ws) is kept public for the initial handshake; auth happens
     *   at the STOMP level.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/refresh",
            "/auth/verify-email/**",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/verify-otp",
            "/auth/set-password",
            "/ws/**"
    };

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, blacklistService);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(SecurityProperties sp,
                                           @Value("${nexmemo.security.rate-limit.redis-enabled:false}") boolean re) {
        return new RateLimitFilter(sp, re);
    }

    @Bean
    public SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityHeadersFilter securityHeadersFilter,
                                                   RateLimitFilter rateLimitFilter,
                                                   JwtAuthenticationFilter jwtAuthFilter) throws Exception {
        http
                // CSRF: For a stateless API using Bearer JWT tokens, traditional CSRF is not needed
                // because the Authorization header is not automatically sent by browsers.
                // However, we still enforce Origin header validation as a defense-in-depth measure.
                .csrf(csrf -> csrf
                        .requireCsrfProtectionMatcher(new OriginRefererCsrfMatcher(allowedOrigins()))
                        .ignoringRequestMatchers(PUBLIC_ENDPOINTS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .requestMatchers("/users/**").hasAnyRole("SUPER_ADMIN", "ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, authEx) -> {
                            res.setContentType("application/json");
                            res.setStatus(401);
                            res.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + authEx.getMessage() + "\"}");
                        })
                        .accessDeniedHandler((req, res, denyEx) -> {
                            res.setContentType("application/json");
                            res.setStatus(403);
                            res.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Access denied\"}");
                        })
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(corsProperties.getAllowedOrigins().split(",")));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Returns the list of allowed origins from CORS configuration for use
     * in the CSRF Origin/Referer validator.
     */
    private List<String> allowedOrigins() {
        return Arrays.asList(corsProperties.getAllowedOrigins().split(","));
    }

    /**
     * Request matcher that validates Origin and Referer headers on state-changing
     * HTTP methods (POST, PUT, DELETE, PATCH). This is a defense-in-depth measure
     * against CSRF that works alongside SameSite=Strict cookies and Bearer token auth.
     *
     * How it works:
     * 1. If the request has an Origin header, it must match one of the allowed origins
     * 2. If no Origin header (e.g., legacy clients), it checks the Referer header
     * 3. If neither header is present, the request is rejected for state-changing methods
     * 4. GET/HEAD/OPTIONS/TRACE requests are exempt (they don't change state)
     */
    private static class OriginRefererCsrfMatcher implements RequestMatcher {

        private static final List<String> SAFE_METHODS = List.of("GET", "HEAD", "OPTIONS", "TRACE");
        private final List<String> allowedOrigins;

        OriginRefererCsrfMatcher(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins.stream()
                    .map(String::trim)
                    .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                    .toList();
        }

        @Override
        public boolean matches(HttpServletRequest request) {
            String method = request.getMethod();
            if (SAFE_METHODS.contains(method.toUpperCase())) {
                return false;
            }

            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");

            // If Origin is present, it must be allowed
            if (origin != null && !origin.isBlank()) {
                boolean matched = allowedOrigins.stream()
                        .anyMatch(allowed -> origin.equals(allowed) || origin.startsWith(allowed + "/"));
                if (!matched) {
                    return true; // block — CSRF match
                }
                return false; // allowed
            }

            // If no Origin, check Referer
            if (referer != null && !referer.isBlank()) {
                boolean matched = allowedOrigins.stream()
                        .anyMatch(allowed -> referer.startsWith(allowed));
                if (!matched) {
                    return true; // block — CSRF match
                }
                return false; // allowed
            }

            // No Origin and no Referer — block state-changing requests
            return true;
        }
    }

}
