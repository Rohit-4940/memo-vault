package com.nexmemo.web.config;

import com.nexmemo.security.service.TokenBlacklistService;
import com.nexmemo.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil, TokenBlacklistService blacklistService) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                try {
                    if (jwtUtil.isTokenValid(token)) {
                        Claims claims = jwtUtil.parseToken(token);
                        String jti = claims.getId();
                        String tokenType = claims.get("type", String.class);

                        if ("ACCESS".equals(tokenType) && blacklistService.isAccessTokenBlacklisted(jti)) {
                            log.warn("Blacklisted token used for WebSocket: jti={}", jti);
                            return null;
                        }

                        String role = claims.get("role", String.class);
                        Long userId = Long.parseLong(claims.getSubject());
                        String username = claims.get("username", String.class);

                        var auth = new UsernamePasswordAuthenticationToken(
                                userId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                        auth.setDetails(username);
                        accessor.setUser(auth);
                        log.debug("WebSocket authenticated: userId={} role={}", userId, role);
                    } else {
                        log.warn("Invalid JWT token for WebSocket connection");
                        return null;
                    }
                } catch (Exception e) {
                    log.error("WebSocket JWT validation error: {}", e.getMessage());
                    return null;
                }
            } else {
                log.warn("No Authorization header in STOMP CONNECT");
                return null;
            }
        }

        return message;
    }
}
