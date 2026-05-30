package com.nexmemo.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenBlacklistService {

    private final StringRedisTemplate stringRedisTemplate;
    private final boolean redisEnabled;
    private final ConcurrentHashMap<String, Long> localBlacklist = new ConcurrentHashMap<>();

    public TokenBlacklistService(
            StringRedisTemplate stringRedisTemplate,
            @org.springframework.beans.factory.annotation.Value("${nexmemo.security.rate-limit.redis-enabled:false}") boolean redisEnabled) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisEnabled = redisEnabled;
        if (!redisEnabled) {
            log.warn("Redis disabled — JWT blacklist is in-memory only. Not suitable for multi-instance deployments.");
            startLocalEviction();
        }
    }

    public void blacklistAccessToken(String jti, long ttlSeconds) {
        if (redisEnabled) {
            stringRedisTemplate.opsForValue().set("jwt:abl:" + jti, "1", ttlSeconds, TimeUnit.SECONDS);
        } else {
            localBlacklist.put(jti, System.currentTimeMillis() + (ttlSeconds * 1000));
        }
        log.debug("Access token blacklisted: {}", jti);
    }

    public boolean isAccessTokenBlacklisted(String jti) {
        if (redisEnabled) {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey("jwt:abl:" + jti));
        }
        Long expiry = localBlacklist.get(jti);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            localBlacklist.remove(jti);
            return false;
        }
        return true;
    }

    public void blacklistRefreshToken(String tokenHash, long ttlSeconds) {
        if (redisEnabled) {
            stringRedisTemplate.opsForValue().set("jwt:rbl:" + tokenHash, "1", ttlSeconds, TimeUnit.SECONDS);
        } else {
            localBlacklist.put("rt:" + tokenHash, System.currentTimeMillis() + (ttlSeconds * 1000));
        }
        log.debug("Refresh token blacklisted: {}", tokenHash);
    }

    public boolean isRefreshTokenBlacklisted(String tokenHash) {
        if (redisEnabled) {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey("jwt:rbl:" + tokenHash));
        }
        Long expiry = localBlacklist.get("rt:" + tokenHash);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            localBlacklist.remove("rt:" + tokenHash);
            return false;
        }
        return true;
    }

    private void startLocalEviction() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-blacklist-eviction");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            localBlacklist.entrySet().removeIf(e -> e.getValue() < now);
        }, 1, 1, TimeUnit.MINUTES);
    }
}
