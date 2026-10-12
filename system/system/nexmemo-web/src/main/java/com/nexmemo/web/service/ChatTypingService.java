package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatTypingEventDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Is typing…" indicators for a conversation (Phase 2g).
 *
 * Typing events are ephemeral: the server emits at most one event per user per conversation
 * every {@code COOLDOWN_MS} and the client clears the indicator on its own timeout. The
 * in-memory cooldown map is single-instance; for multi-instance deployments swap this for the
 * Redis pub/sub path (the {@code /topic/conversation/{id}/typing} destination is identical).
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatTypingService {

    private static final long COOLDOWN_MS = 1500;

    private final Map<String, Long> lastTypingAt = new ConcurrentHashMap<>();

    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void notifyTyping(Long userId, Long conversationId) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);

        long now = System.currentTimeMillis();
        String key = conversationId + ":" + userId;
        Long last = lastTypingAt.get(key);
        if (last != null && now - last < COOLDOWN_MS) {
            return; // rate-limited — drop this frame
        }
        lastTypingAt.put(key, now);

        String name = userRepository.findById(userId).map(User::getName).orElse("User #" + userId);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/typing",
                ChatTypingEventDto.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .displayName(name)
                        .at(LocalDateTime.now())
                        .build());
    }
}