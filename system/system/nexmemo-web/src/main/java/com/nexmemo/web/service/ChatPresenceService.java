package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatPresenceDto;
import com.nexmemo.core.dto.ChatPresenceEventDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatParticipant;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Presence tracking for chat (Phase 2g).
 *
 * Driven by WebSocket connect/disconnect events (see {@code ChatPresenceWebSocketListener}).
 * A user with several open tabs/sessions is online until the last session closes. Because the
 * map is in-memory this is single-instance; multi-instance deployments should back it with the
 * Redis pub/sub + expiring keys (the wire format is unchanged).
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceService {

    private static final long STALE_AFTER_MS = 5 * 60 * 1000;

    private final Map<Long, Presence> presence = new ConcurrentHashMap<>();

    private final ChatParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;
    private final SimpMessagingTemplate messagingTemplate;

    public void setOnline(Long userId) {
        if (userId == null) {
            return;
        }
        Presence p = presence.computeIfAbsent(userId, k -> new Presence());
        p.sessions.incrementAndGet();
        p.lastSeenMillis = System.currentTimeMillis();
        broadcast(userId, true);
    }

    public void setOffline(Long userId) {
        if (userId == null) {
            return;
        }
        Presence p = presence.get(userId);
        if (p == null) {
            return;
        }
        if (p.sessions.decrementAndGet() <= 0) {
            presence.remove(userId);
            broadcast(userId, false);
        } else {
            p.lastSeenMillis = System.currentTimeMillis();
        }
    }

    public boolean isOnline(Long userId) {
        Presence p = presence.get(userId);
        if (p == null) {
            return false;
        }
        if (System.currentTimeMillis() - p.lastSeenMillis > STALE_AFTER_MS) {
            presence.remove(userId);
            return false;
        }
        return true;
    }

    public LocalDateTime lastSeen(Long userId) {
        Presence p = presence.get(userId);
        if (p == null || p.lastSeenMillis == 0) {
            return null;
        }
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(p.lastSeenMillis),
                java.time.ZoneId.systemDefault());
    }

    /** Presence of every active participant, for the member list / header indicator. */
    public List<ChatPresenceDto> conversationPresence(Long viewerId, Long conversationId) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(viewerId, conversation);

        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
        List<Long> ids = participants.stream().map(ChatParticipant::getUserId).distinct().toList();
        Map<Long, String> names = new ConcurrentHashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(u -> names.put(u.getId(), u.getName()));
        }
        List<ChatPresenceDto> result = new ArrayList<>(participants.size());
        for (ChatParticipant p : participants) {
            result.add(ChatPresenceDto.builder()
                    .userId(p.getUserId())
                    .displayName(names.getOrDefault(p.getUserId(), "User #" + p.getUserId()))
                    .online(isOnline(p.getUserId()))
                    .lastSeen(lastSeen(p.getUserId()))
                    .build());
        }
        result.sort((a, b) -> Boolean.compare(b.isOnline(), a.isOnline()));
        return result;
    }

    /** Fills display names for a set of ids (used by the members endpoint). */
    public Map<Long, String> namesFor(List<Long> userIds) {
        Map<Long, String> names = new ConcurrentHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return names;
        }
        userRepository.findAllById(userIds).forEach(u -> names.put(u.getId(), u.getName()));
        return names;
    }

    private void broadcast(Long userId, boolean online) {
        List<Long> conversationIds;
        try {
            conversationIds = participantRepository.findActiveConversationIdsByUserId(userId);
        } catch (Exception e) {
            conversationIds = List.of();
        }
        ChatPresenceEventDto event = ChatPresenceEventDto.builder()
                .userId(userId)
                .online(online)
                .at(LocalDateTime.now())
                .build();
        for (Long conversationId : conversationIds) {
            messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/presence", event);
        }
        log.debug("Presence {} for userId={} broadcast to {} conversations", online ? "online" : "offline",
                userId, conversationIds.size());
    }

    /** Per-user session counter + last-seen millis (concurrent-map safe). */
    private static final class Presence {
        private final AtomicInteger sessions = new AtomicInteger(0);
        private volatile long lastSeenMillis = System.currentTimeMillis();
    }
}