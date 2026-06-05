package com.nexmemo.web.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caching configuration using Caffeine (in-memory, zero-infrastructure).
 *
 * Why Caffeine over Redis for this use case:
 * - Reference data (departments, branches, memo types, fiscal years) changes
 *   rarely and is small enough to fit in heap memory
 * - No network hop — faster than Redis for these lookups
 * - Zero infrastructure — works in single-instance and multi-instance with
 *   minimal overhead
 * - For multi-instance deployments where cache invalidation is critical,
 *   Redis can be added as a secondary cache layer later
 *
 * Cache regions:
 * - "referenceData" — departments, branches, memo types, fiscal years, etc.
 *   TTL: 1 hour, max 500 entries
 * - "users" — user lookups by ID
 *   TTL: 5 minutes, max 1000 entries
 * - "memos" — memo list queries (short TTL to stay fresh)
 *   TTL: 30 seconds, max 200 entries
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .recordStats());
        manager.registerCustomCache("referenceData",
                Caffeine.newBuilder()
                        .initialCapacity(100)
                        .maximumSize(500)
                        .expireAfterWrite(1, TimeUnit.HOURS)
                        .recordStats()
                        .build());
        manager.registerCustomCache("users",
                Caffeine.newBuilder()
                        .initialCapacity(50)
                        .maximumSize(1000)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
                        .build());
        manager.registerCustomCache("memos",
                Caffeine.newBuilder()
                        .initialCapacity(20)
                        .maximumSize(200)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .recordStats()
                        .build());
        return manager;
    }
}
