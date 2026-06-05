package com.nexmemo.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 *
 * IMPORTANT: CORS is configured in SecurityConfig via CorsConfigurationSource.
 * Do NOT add CORS here — it would override the SecurityConfig CORS and
 * create a security hole (wildcard origins with credentials).
 *
 * See: SecurityConfig.corsConfigurationSource()
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

}
