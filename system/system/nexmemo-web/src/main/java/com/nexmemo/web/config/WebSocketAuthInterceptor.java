package com.nexmemo.web.config;

import com.nexmemo.security.service.TokenBlacklistService;
import com.nexmemo.security.util.JwtUtil;
import com.nexmemo.web.service.ChatAuthorizationService;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

/**
 * Authenticates the STOMP CONNECT frame and authorizes every SUBSCRIBE.
 *
 * SECURITY:
 *  - CONNECT: the JWT is validated (signature, expiry, blacklist) and the principal is
 *    bound to the session.
 *  - SUBSCRIBE: destinations are checked against the same resource authorization used by
 *    the REST layer, so a client cannot eavesdrop on another branch's topic, another user's
 *    queue, or another user's notification stream. Unknown destinations are denied by default.
 *
 * @author : NexMemo
 **/
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String TOPIC_BRANCH_PREFIX = "/topic/branch/";
    private static final String TOPIC_CONVERSATION_PREFIX = "/topic/conversation/";
    private static final String TOPIC_NOTIFICATIONS_PREFIX = "/topic/notifications/";
    private static final String QUEUE_MESSAGES_PREFIX = "/queue/messages/";

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;
    private final ChatAuthorizationService chatAuthorizationService;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil,
                                    TokenBlacklistService blacklistService,
                                    ChatAuthorizationService chatAuthorizationService) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
        this.chatAuthorizationService = chatAuthorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return authenticate(accessor) ? message : null;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return authorizeSubscription(accessor) ? message : null;
        }

        return message;
    }

    private boolean authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("No Authorization header in STOMP CONNECT");
            return false;
        }
        String token = authHeader.substring(7);
        try {
            if (!jwtUtil.isTokenValid(token)) {
                log.warn("Invalid JWT token for WebSocket connection");
                return false;
            }
            Claims claims = jwtUtil.parseToken(token);
            String jti = claims.getId();
            String tokenType = claims.get("type", String.class);
            if ("ACCESS".equals(tokenType) && blacklistService.isAccessTokenBlacklisted(jti)) {
                log.warn("Blacklisted token used for WebSocket: jti={}", jti);
                return false;
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
            return true;
        } catch (Exception e) {
            log.error("WebSocket JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    private boolean authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return true;
        }

        // Spring translates /user/** subscriptions to a session-private queue, so these are
        // inherently scoped to the authenticated principal.
        if (destination.startsWith("/user/")) {
            return true;
        }

        Long userId = currentUserId(accessor.getUser());
        if (userId == null) {
            log.warn("Rejected unauthenticated SUBSCRIBE to {}", destination);
            return false;
        }

        if (destination.startsWith(TOPIC_BRANCH_PREFIX)) {
            Long branchId = parseTrailingId(destination, TOPIC_BRANCH_PREFIX);
            if (branchId != null && chatAuthorizationService.canAccessBranch(userId, branchId)) {
                return true;
            }
            log.warn("Blocked SUBSCRIBE userId={} to {}", userId, destination);
            return false;
        }

        if (destination.startsWith(TOPIC_CONVERSATION_PREFIX)) {
            Long conversationId = parseTrailingId(destination, TOPIC_CONVERSATION_PREFIX);
            if (conversationId != null && chatAuthorizationService.canAccessConversation(userId, conversationId)) {
                return true;
            }
            log.warn("Blocked SUBSCRIBE userId={} to {}", userId, destination);
            return false;
        }

        if (destination.startsWith(TOPIC_NOTIFICATIONS_PREFIX)) {
            Long target = parseTrailingId(destination, TOPIC_NOTIFICATIONS_PREFIX);
            if (userId.equals(target)) {
                return true;
            }
            log.warn("Blocked SUBSCRIBE userId={} to {}", userId, destination);
            return false;
        }

        if (destination.startsWith(QUEUE_MESSAGES_PREFIX)) {
            Long target = parseTrailingId(destination, QUEUE_MESSAGES_PREFIX);
            if (userId.equals(target)) {
                return true;
            }
            log.warn("Blocked SUBSCRIBE userId={} to {}", userId, destination);
            return false;
        }

        // Deny-by-default: any destination we do not explicitly recognize.
        log.warn("Blocked SUBSCRIBE userId={} to unrecognized destination {}", userId, destination);
        return false;
    }

    private static Long currentUserId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        return null;
    }

    private static Long parseTrailingId(String destination, String prefix) {
        String tail = destination.substring(prefix.length()).trim();
        // Ignore any further path segments; the id is the first segment.
        int slash = tail.indexOf('/');
        if (slash >= 0) {
            tail = tail.substring(0, slash);
        }
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
