package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatReactionEventDto;
import com.nexmemo.core.dto.ChatReactionSummaryDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageReaction;
import com.nexmemo.security.repository.ChatMessageReactionRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Emoji reactions on conversation messages.
 *
 * Every operation resolves the caller from the JWT principal and re-checks conversation access
 * server-side. Reacting to a soft-deleted message is rejected.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatReactionService {

    /** Keep a single emoji/sequence short; the DB column is NVARCHAR(32). */
    private static final int MAX_EMOJI_LENGTH = 32;
    private static final int MAX_USER_IDS_IN_SUMMARY = 50;

    private final ChatMessageReactionRepository reactionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;
    private final SimpMessagingTemplate messagingTemplate;

    public List<ChatReactionSummaryDto> addReaction(Long userId, Long messageId, String emoji) {
        String clean = sanitizeEmoji(emoji);
        ChatMessage message = requireReactableMessage(userId, messageId);

        Optional<ChatMessageReaction> existing =
                reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, clean);
        if (existing.isEmpty()) {
            reactionRepository.save(ChatMessageReaction.builder()
                    .messageId(messageId)
                    .userId(userId)
                    .emoji(clean)
                    .build());
            broadcast(message, clean, userId, "ADDED");
            log.debug("Reaction {} added to message {} by userId={}", clean, messageId, userId);
        }
        return summarize(reactionRepository.findByMessageIdOrderByCreatedDateAsc(messageId), userId);
    }

    public List<ChatReactionSummaryDto> removeReaction(Long userId, Long messageId, String emoji) {
        String clean = sanitizeEmoji(emoji);
        ChatMessage message = requireReactableMessage(userId, messageId);

        reactionRepository.findByMessageIdAndUserIdAndEmoji(messageId, userId, clean)
                .ifPresent(r -> {
                    reactionRepository.delete(r);
                    broadcast(message, clean, userId, "REMOVED");
                    log.debug("Reaction {} removed from message {} by userId={}", clean, messageId, userId);
                });
        return summarize(reactionRepository.findByMessageIdOrderByCreatedDateAsc(messageId), userId);
    }

    @Transactional(readOnly = true)
    public List<ChatReactionSummaryDto> listReactions(Long userId, Long messageId) {
        requireReactableMessage(userId, messageId);
        return summarize(reactionRepository.findByMessageIdOrderByCreatedDateAsc(messageId), userId);
    }

    // ── summary / realtime helpers ────────────────────────────────────────────

    /**
     * Groups flat reaction rows into per-emoji summaries. Ordering is deterministic (emoji asc)
     * so the UI does not reshuffle chips between renders.
     */
    public static List<ChatReactionSummaryDto> summarize(List<ChatMessageReaction> reactions, Long viewerId) {
        if (reactions == null || reactions.isEmpty()) {
            return List.of();
        }
        Map<String, List<ChatMessageReaction>> byEmoji = new TreeMap<>();
        for (ChatMessageReaction r : reactions) {
            byEmoji.computeIfAbsent(r.getEmoji(), k -> new ArrayList<>()).add(r);
        }
        List<ChatReactionSummaryDto> result = new ArrayList<>(byEmoji.size());
        byEmoji.forEach((emoji, rows) -> {
            List<Long> userIds = new ArrayList<>(Math.min(rows.size(), MAX_USER_IDS_IN_SUMMARY));
            boolean mine = false;
            for (ChatMessageReaction r : rows) {
                if (userIds.size() < MAX_USER_IDS_IN_SUMMARY) {
                    userIds.add(r.getUserId());
                }
                if (viewerId != null && viewerId.equals(r.getUserId())) {
                    mine = true;
                }
            }
            result.add(ChatReactionSummaryDto.builder()
                    .emoji(emoji)
                    .count(rows.size())
                    .reactedByMe(mine)
                    .userIds(userIds)
                    .build());
        });
        result.sort(Comparator.comparingInt(ChatReactionSummaryDto::getCount).reversed());
        return result;
    }

    /** Groups a page of reactions by message id for history rendering. */
    public static Map<Long, List<ChatMessageReaction>> groupByMessage(List<ChatMessageReaction> reactions) {
        Map<Long, List<ChatMessageReaction>> map = new LinkedHashMap<>();
        if (reactions == null) {
            return map;
        }
        for (ChatMessageReaction r : reactions) {
            map.computeIfAbsent(r.getMessageId(), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    private void broadcast(ChatMessage message, String emoji, Long userId, String action) {
        long count = reactionRepository.countByMessageIdAndEmoji(message.getId(), emoji);
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + message.getConversationId() + "/reactions",
                ChatReactionEventDto.builder()
                        .conversationId(message.getConversationId())
                        .messageId(message.getId())
                        .emoji(emoji)
                        .userId(userId)
                        .action(action)
                        .count(count)
                        .build());
    }

    private ChatMessage requireReactableMessage(Long userId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (message.getConversationId() == null) {
            throw new IllegalArgumentException("Reactions require a conversation message");
        }
        ChatConversation conversation = conversationService.requireConversation(message.getConversationId());
        chatAuth.assertCanAccessConversation(userId, conversation);
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new IllegalArgumentException("Cannot react to a deleted message");
        }
        return message;
    }

    private static String sanitizeEmoji(String emoji) {
        if (emoji == null) {
            throw new IllegalArgumentException("emoji is required");
        }
        String clean = emoji.trim();
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("emoji is required");
        }
        if (clean.length() > MAX_EMOJI_LENGTH) {
            throw new IllegalArgumentException("emoji is too long");
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                throw new IllegalArgumentException("emoji contains invalid characters");
            }
        }
        return clean;
    }
}
