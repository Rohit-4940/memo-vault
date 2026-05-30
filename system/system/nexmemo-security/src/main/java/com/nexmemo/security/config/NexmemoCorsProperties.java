package com.nexmemo.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Data
@Component
@ConfigurationProperties(prefix = "nexmemo.cors")
public class NexmemoCorsProperties {
    /** Comma-separated list: http://localhost:3000,http://localhost:4200,http://localhost:4202 */
    private String allowedOrigins = "http://localhost:3000,http://localhost:4200,http://localhost:4202";
    private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS,PATCH";
    private String allowedHeaders  = "*";
    private boolean allowCredentials = true;
    private long maxAge = 3600L;
}
