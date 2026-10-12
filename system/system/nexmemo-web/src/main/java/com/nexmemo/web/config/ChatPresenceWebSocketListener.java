package com.nexmemo.web.config;

import com.nexmemo.web.service.ChatPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Marks a user online/offline as their STOMP session connects/disconnects (Phase 2g).
 *
 * The presence cache is updated for every session; a user with multiple tabs stays online until
 * the last session closes. The session principal is the {@link Authentication} created by
 * {@link WebSocketAuthInterceptor} (principal = {@code Long} userId).
 *
 * @author : NexMemo
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPresenceWebSocketListener {

    private final ChatPresenceService presenceService;

    @EventListener
    public void onSessionConnected(SessionConnectedEvent event) {
        Long userId = userId(event.getUser());
        if (userId != null) {
            presenceService.setOnline(userId);
            log.debug("WebSocket connected for userId={}", userId);
        }
    }

    @EventListener
    public void onSessionDisconnected(SessionDisconnectEvent event) {
        Long userId = userId(event.getUser());
        if (userId != null) {
            presenceService.setOffline(userId);
            log.debug("WebSocket disconnected for userId={}", userId);
        }
    }

    private static Long userId(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
                // principal name is not a user id — ignore this session
            }
        }
        return null;
    }
}