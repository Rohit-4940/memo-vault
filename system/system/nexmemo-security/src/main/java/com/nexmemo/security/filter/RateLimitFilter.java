package com.nexmemo.security.filter;

import com.nexmemo.core.dto.SecurityProperties;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityProperties.RateLimit rateLimitConfig;
    private final boolean redisEnabled;

    private final Map<String, Bucket> localLoginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> localOtpBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> localApiBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> localPasswordResetBuckets = new ConcurrentHashMap<>();

    private LettuceBasedProxyManager<byte[]> redisProxyManager;

    public RateLimitFilter(SecurityProperties securityProperties,
                           @Value("${nexmemo.security.rate-limit.redis-enabled:false}") boolean redisEnabled) {
        this.rateLimitConfig = securityProperties.getRateLimit();
        this.redisEnabled = redisEnabled;
        if (redisEnabled) {
            try {
                RedisClient redisClient = RedisClient.create("redis://localhost:6379");
                this.redisProxyManager = Bucket4jLettuce.casBasedBuilder(redisClient).build();
                log.info("Redis rate limiting enabled");
            } catch (Exception e) {
                log.error("Failed to connect to Redis for rate limiting, falling back to local: {}", e.getMessage());
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String ip = extractIp(req);
        String uri = req.getRequestURI();
        String key = ip + ":" + req.getHeader("User-Agent");

        Bucket bucket;
        String limitLabel;

        if (uri.contains("/auth/login")) {
            bucket = resolveBucket(key, "login", rateLimitConfig.getLoginMaxPerMinute(), Duration.ofMinutes(1));
            limitLabel = "LOGIN";
        } else if (uri.contains("/auth/verify-otp")) {
            bucket = resolveBucket(key, "otp", rateLimitConfig.getOtpMaxPer5Minutes(), Duration.ofMinutes(5));
            limitLabel = "OTP";
        } else if (uri.contains("/auth/forgot-password") || uri.contains("/auth/reset-password")) {
            bucket = resolveBucket(key, "pwd-reset", rateLimitConfig.getPasswordResetMaxPerHour(), Duration.ofHours(1));
            limitLabel = "PASSWORD_RESET";
        } else if (uri.startsWith("/api/")) {
            bucket = resolveBucket(ip, "api", rateLimitConfig.getApiMaxPerMinute(), Duration.ofMinutes(1));
            limitLabel = "API";
        } else {
            chain.doFilter(req, res);
            return;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            res.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(req, res);
        } else {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            log.warn("RATE LIMIT: {} exceeded for IP={} URI={}", limitLabel, ip, uri);

            res.setStatus(429);
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("Retry-After", String.valueOf(waitSeconds));
            res.getWriter().write(String.format(
                    "{\"error\":\"TOO_MANY_REQUESTS\"," +
                    "\"message\":\"Rate limit exceeded. Try again in %d seconds.\"," +
                    "\"retryAfterSeconds\":%d}",
                    waitSeconds, waitSeconds));
        }
    }

    private Bucket resolveBucket(String keyStr, String type, int capacity, Duration period) {
        if (redisEnabled && redisProxyManager != null) {
            try {
                Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, period));
                BucketConfiguration config = BucketConfiguration.builder().addLimit(limit).build();
                byte[] key = keyStr.getBytes(StandardCharsets.UTF_8);
                return redisProxyManager.builder().build(key, config);
            } catch (Exception e) {
                log.warn("Redis bucket resolution failed for {}, falling back to local", keyStr);
            }
        }
        return getLocalBucket(keyStr, type, capacity, period);
    }

    private Bucket getLocalBucket(String key, String type, int capacity, Duration period) {
        Map<String, Bucket> buckets = switch (type) {
            case "login" -> localLoginBuckets;
            case "otp" -> localOtpBuckets;
            case "api" -> localApiBuckets;
            case "pwd-reset" -> localPasswordResetBuckets;
            default -> localApiBuckets;
        };
        return buckets.computeIfAbsent(key, k -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, period)))
                .build());
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : req.getRemoteAddr();
    }
}
