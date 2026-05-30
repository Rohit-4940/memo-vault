package com.nexmemo.security.util;

import com.nexmemo.core.dto.SecurityProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final SecurityProperties securityProperties;

    private SecretKey key() {
        byte[] bytes = Decoders.BASE64.decode(securityProperties.getJwt().getSecret());
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(securityProperties.getJwt().getAccessTokenExpiryMinutes() * 60L);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(securityProperties.getJwt().getIssuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", "ACCESS")
                .claim("token_family", UUID.randomUUID().toString().substring(0, 8))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    public String generateAccessTokenWithFamily(Long userId, String username, String role, String tokenFamily) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(securityProperties.getJwt().getAccessTokenExpiryMinutes() * 60L);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(securityProperties.getJwt().getIssuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("type", "ACCESS")
                .claim("token_family", tokenFamily)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(securityProperties.getJwt().getRefreshTokenExpiryDays() * 86400L);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(securityProperties.getJwt().getIssuer())
                .subject(String.valueOf(userId))
                .claim("type", "REFRESH")
                .claim("token_family", UUID.randomUUID().toString().substring(0, 8))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    public String generateRefreshTokenWithFamily(Long userId, String tokenFamily) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(securityProperties.getJwt().getRefreshTokenExpiryDays() * 86400L);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(securityProperties.getJwt().getIssuer())
                .subject(String.valueOf(userId))
                .claim("type", "REFRESH")
                .claim("token_family", tokenFamily)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
        }
        return false;
    }

    public String extractJti(String token) {
        try {
            return parseToken(token).getId();
        } catch (Exception e) {
            return null;
        }
    }

    public String extractTokenFamily(String token) {
        try {
            return parseToken(token).get("token_family", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public String extractTokenType(String token) {
        try {
            return parseToken(token).get("type", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String extractUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String extractRole(String token) {
        return parseToken(token).get("role", String.class);
    }
}
