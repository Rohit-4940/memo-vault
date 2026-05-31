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

import java.util.Arrays;


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

    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/login",
            "/auth/refresh",
            "/auth/verify-email/**",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/verify-otp",
            "/auth/set-password",
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**"
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
                .csrf(AbstractHttpConfigurer::disable)
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

}
