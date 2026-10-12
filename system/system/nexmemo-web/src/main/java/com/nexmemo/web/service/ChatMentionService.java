package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMentionDto;
import com.nexmemo.core.dto.ChatMentionEventDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageMention;
import com.nexmemo.core.entity.ChatParticipant;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatNotificationLevel;
import com.nexmemo.security.repository.ChatMessageMentionRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @mention handling for conversation messages.
 *
 * Mentions are encoded by the composer as {@code @[Display Name](userId)} tokens. The server
 * re-parses the raw body — the client is never trusted — and:
 * <ul>
 *   <li>drops self-mentions and unknown users;</li>
 *   <li>drops users who could not read the conversation anyway
 *       ({@link ChatAuthorizationService#canAccessConversation}) so a notification can never
 *       leak message content to an unauthorized recipient;</li>
 *   <li>persists one {@code chat_message_mention} row per tagged user (unique per message+user);</li>
 *   <li>creates an in-app notification and a realtime event for <em>newly</em> added mentions.</li>
 * </ul>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatMentionService {

    /** Bound the work per message: more than this is almost certainly abuse. */
    static final int MAX_MENTIONS_PER_MESSAGE = 25;
    private static final int PREVIEW_MAX = 120;
    private static final Pattern MENTION =
            Pattern.compile("@\\[([^\\]\\r\\n]{1,80})\\]\\((\\d{1,19})\\)");

    private final ChatMessageMentionRepository mentionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService chatAuth;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    // ── parsing ───────────────────────────────────────────────────────────────

    /** Extracts distinct mentioned user ids from a raw message body (order preserved). */
    public static List<Long> parseMentionedIds(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Matcher matcher = MENTION.matcher(content);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(2)));
            } catch (NumberFormatException ignored) {
                // 19-digit bound cannot overflow; guard anyway.
            }
        }
        return new ArrayList<>(ids);
    }

    /** Renders mention tokens as plain {@code @Name}, collapses whitespace and truncates. */
    public static String preview(String content) {
        if (content == null) {
            return "";
        }
        String plain = MENTION.matcher(content).replaceAll("@$1").replaceAll("\\s+", " ").trim();
        if (plain.length() > PREVIEW_MAX) {
            plain = plain.substring(0, PREVIEW_MAX - 3) + "...";
        }
        return plain;
    }

    // ── sync on send / edit ───────────────────────────────────────────────────

    /**
     * Reconciles the persisted mentions for a message against its current body. Stale rows are
     * removed, new rows are inserted, and only newly added users are notified.
     */
    public void syncMentions(ChatConversation conversation, ChatMessage message, Long senderId) {
        if (conversation == null || message == null || message.getId() == null) {
            return;
        }

        List<Long> allowed = resolveAuthorizedTargets(conversation, message.getMessage(), senderId);
        Set<Long> allowedSet = new LinkedHashSet<>(allowed);

        List<ChatMessageMention> existing = mentionRepository.findByMessageId(message.getId());
        for (ChatMessageMention row : existing) {
            if (!allowedSet.contains(row.getMentionedUserId())) {
                mentionRepository.delete(row);
            }
        }
        Set<Long> existingIds = new LinkedHashSet<>();
        existing.forEach(row -> existingIds.add(row.getMentionedUserId()));

        List<Long> newlyMentioned = new ArrayList<>();
        for (Long userId : allowed) {
            if (!existingIds.contains(userId)) {
                mentionRepository.save(ChatMessageMention.builder()
                        .messageId(message.getId())
                        .conversationId(message.getConversationId())
                        .mentionedUserId(userId)
                        .build());
                newlyMentioned.add(userId);
            }
        }

        if (!newlyMentioned.isEmpty()) {
            notifyMentioned(conversation, message, senderId, newlyMentioned);
        }
    }

    /** Removes mentions attached to a message (used when the message is deleted). */
    public void removeForMessage(Long messageId) {
        if (messageId != null) {
            mentionRepository.deleteByMessageId(messageId);
        }
    }

    /** MUTE = the user opted out of all noise from this conversation (fetch is null-safe). */
    private ChatNotificationLevel notificationLevelOf(ChatConversation conversation, Long userId) {
        if (conversation == null || conversation.getId() == null || userId == null) {
            return ChatNotificationLevel.ALL;
        }
        java.util.Optional<ChatParticipant> found =
                participantRepository.findByConversationIdAndUserId(conversation.getId(), userId);
        if (found == null || found.isEmpty()) {
            return ChatNotificationLevel.ALL;
        }
        ChatNotificationLevel level = found.get().getNotificationLevel();
        return level == null ? ChatNotificationLevel.ALL : level;
    }

    private List<Long> resolveAuthorizedTargets(ChatConversation conversation, String content, Long senderId) {
        List<Long> parsed = parseMentionedIds(content);
        List<Long> allowed = new ArrayList<>();
        for (Long userId : parsed) {
            if (allowed.size() >= MAX_MENTIONS_PER_MESSAGE) {
                log.warn("Mention cap reached for conversation {}: extra tags ignored", conversation.getId());
                break;
            }
            if (userId.equals(senderId)) {
                continue; // never mention yourself
            }
            if (!userRepository.existsById(userId)) {
                continue; // unknown user
            }
            if (!chatAuth.canAccessConversation(userId, conversation)) {
                log.debug("Dropping mention of userId={} (no access to conversation {})",
                        userId, conversation.getId());
                continue;
            }
            // Per-conversation notification preference (Phase 2h): MUTE means the user wants
            // zero noise from this conversation — not even tags. MENTIONS/ALL still tag.
            if (notificationLevelOf(conversation, userId) == ChatNotificationLevel.MUTE) {
                continue;
            }
            allowed.add(userId);
        }
        return allowed;
    }

    private void notifyMentioned(ChatConversation conversation, ChatMessage message,
                                 Long senderId, List<Long> userIds) {
        String senderName = userRepository.findById(senderId).map(User::getName).orElse("Someone");
        String preview = preview(message.getMessage());

        for (Long userId : userIds) {
            notificationService.createNotification(
                    userId,
                    "You were mentioned",
                    senderName + " mentioned you: " + preview,
                    "CHAT_MENTION",
                    conversation.getId(),
                    "CONVERSATION");

            messagingTemplate.convertAndSend("/topic/notifications/" + userId,
                    ChatMentionEventDto.builder()
                            .conversationId(conversation.getId())
                            .messageId(message.getId())
                            .mentionedUserId(userId)
                            .mentionedByUserId(senderId)
                            .mentionedByName(senderName)
                            .preview(preview)
                            .build());
        }
        log.info("Mentioned {} user(s) in conversation {} by userId={}", userIds.size(),
                conversation.getId(), senderId);
    }

    // ── read model ────────────────────────────────────────────────────────────

    /** Groups mention rows by message id (bulk history rendering). */
    public static Map<Long, List<Long>> groupByMessage(List<ChatMessageMention> mentions) {
        Map<Long, List<Long>> map = new LinkedHashMap<>();
        if (mentions == null) {
            return map;
        }
        for (ChatMessageMention m : mentions) {
            map.computeIfAbsent(m.getMessageId(), k -> new ArrayList<>()).add(m.getMentionedUserId());
        }
        return map;
    }

    /** The caller's tagged inbox, newest first. Scoped to the principal — no IDOR surface. */
    @Transactional(readOnly = true)
    public List<ChatMentionDto> getMyMentions(Long userId, int size) {
        int capped = Math.min(Math.max(size, 1), 50);
        List<ChatMessageMention> rows = mentionRepository.findByMentionedUserIdOrderByIdDesc(
                userId, PageRequest.of(0, capped));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> messageIds = rows.stream().map(ChatMessageMention::getMessageId).distinct().toList();
        Map<Long, ChatMessage> messages = new LinkedHashMap<>();
        messageRepository.findAllById(messageIds).forEach(m -> messages.put(m.getId(), m));

        List<Long> senderIds = messages.values().stream()
                .map(ChatMessage::getSenderId).distinct().toList();
        Map<Long, String> senderNames = new LinkedHashMap<>();
        userRepository.findAllById(senderIds).forEach(u -> senderNames.put(u.getId(), u.getName()));

        List<ChatMentionDto> result = new ArrayList<>(rows.size());
        for (ChatMessageMention row : rows) {
            ChatMessage message = messages.get(row.getMessageId());
            if (message == null || Boolean.TRUE.equals(message.getIsDeleted())) {
                continue; // never surface a deleted message's mention
            }
            result.add(ChatMentionDto.builder()
                    .messageId(row.getMessageId())
                    .conversationId(row.getConversationId())
                    .mentionedUserId(row.getMentionedUserId())
                    .mentionedByUserId(message.getSenderId())
                    .mentionedByName(senderNames.getOrDefault(message.getSenderId(),
                            "User #" + message.getSenderId()))
                    .preview(ChatMentionService.preview(message.getMessage()))
                    .createdDate(row.getCreatedDate())
                    .build());
        }
        return result;
    }
}
