package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatConversationLockEventDto;
import com.nexmemo.core.dto.ChatConversationSummaryDto;
import com.nexmemo.core.dto.ChatMemberDto;
import com.nexmemo.core.dto.ChatReceiptEventDto;
import com.nexmemo.core.dto.CreateConversationRequest;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageReceipt;
import com.nexmemo.core.entity.ChatParticipant;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.core.enums.ChatNotificationLevel;
import com.nexmemo.core.enums.ChatParticipantRole;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatMessageReceiptRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversation lifecycle: creation (idempotent for non-group types), membership, listing
 * and read tracking.
 *
 * Authorization is delegated to {@link ChatAuthorizationService}; membership rows are
 * created lazily on first join so unread tracking works for every conversation type.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatConversationService {

    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatMessageReceiptRepository receiptRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService chatAuth;
    private final SimpMessagingTemplate messagingTemplate;

    /** Lazy presence lookup — breaks the ConversationService ⇄ PresenceService cycle. */
    private final ObjectProvider<ChatPresenceService> presenceProvider;

    /**
     * Conversation types for which per-message receipt rows are materialized. Large fan-out
     * channels (BRANCH/DEPARTMENT/MEMO) rely solely on participant watermarks to avoid a
     * receipt row per member per message.
     */
    private static final java.util.Set<ChatConversationType> RECEIPT_TYPES =
            java.util.EnumSet.of(ChatConversationType.DIRECT, ChatConversationType.GROUP);

    // ── Create / get-or-create ────────────────────────────────────────────────

    public ChatConversation createOrGet(Long userId, CreateConversationRequest req) {
        ChatConversationType type = parseType(req.getConversationType());
        return switch (type) {
            case DIRECT -> createOrGetDirect(userId, req.getOtherUserId());
            case GROUP -> createGroup(userId, req.getTitle(), req.getMemberIds());
            case BRANCH -> createOrGetTyped(userId, type, null, req.getBranchId(), null, "BRANCH:" + req.getBranchId());
            case DEPARTMENT -> createOrGetTyped(userId, type, null, null, req.getDepartmentId(), "DEPT:" + req.getDepartmentId());
            case MEMO -> createOrGetTyped(userId, type, null, null, req.getMemoId(), "MEMO:" + req.getMemoId());
        };
    }

    private ChatConversation createOrGetDirect(Long userId, Long otherUserId) {
        chatAuth.assertCanDirectMessage(userId, otherUserId);
        long a = Math.min(userId, otherUserId);
        long b = Math.max(userId, otherUserId);
        String key = "DIRECT:" + a + ":" + b;

        ChatConversation conversation = conversationRepository.findByDedupeKey(key)
                .orElseGet(() -> {
                    ChatConversation c = ChatConversation.builder()
                            .conversationType(ChatConversationType.DIRECT)
                            .dedupeKey(key)
                            .isArchived(false)
                            .isLocked(false)
                            .build();
                    return conversationRepository.save(c);
                });
        joinIfNeeded(conversation, userId, ChatParticipantRole.MEMBER);
        joinIfNeeded(conversation, otherUserId, ChatParticipantRole.MEMBER);
        return conversation;
    }

    private ChatConversation createOrGetTyped(Long userId, ChatConversationType type, String title,
                                              Long branchId, Long departmentId, String dedupeKey) {
        // Resource authorization is type-specific and must run regardless of existence.
        switch (type) {
            case BRANCH -> chatAuth.assertCanAccessBranch(userId, branchId);
            case DEPARTMENT -> chatAuth.assertCanAccessDepartment(userId, departmentId);
            case MEMO -> chatAuth.assertCanAccessMemo(userId, resolveMemoId(dedupeKey));
            default -> throw new IllegalArgumentException("Unsupported typed conversation: " + type);
        }
        ChatConversation conversation = conversationRepository.findByDedupeKey(dedupeKey)
                .orElseGet(() -> {
                    ChatConversation c = ChatConversation.builder()
                            .conversationType(type)
                            .title(title)
                            .branchId(branchId)
                            .departmentId(departmentId)
                            .memoId(resolveMemoId(dedupeKey))
                            .dedupeKey(dedupeKey)
                            .isArchived(false)
                            .isLocked(false)
                            .build();
                    return conversationRepository.save(c);
                });
        joinIfNeeded(conversation, userId, ChatParticipantRole.MEMBER);
        return conversation;
    }

    private ChatConversation createGroup(Long userId, String title, List<Long> memberIds) {
        chatAuth.assertCanCreateGroup(userId);
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Group title is required");
        }
        ChatConversation conversation = ChatConversation.builder()
                .conversationType(ChatConversationType.GROUP)
                .title(title.trim())
                .isArchived(false)
                .isLocked(false)
                .build();
        conversation = conversationRepository.save(conversation);
        joinIfNeeded(conversation, userId, ChatParticipantRole.OWNER);
        if (memberIds != null) {
            for (Long memberId : memberIds) {
                if (memberId == null || memberId.equals(userId)) {
                    continue;
                }
                assertActiveUser(memberId);
                joinIfNeeded(conversation, memberId, ChatParticipantRole.MEMBER);
            }
        }
        log.info("Group conversation {} created by userId={} with {} members",
                conversation.getId(), userId, conversationRepository.count());
        return conversation;
    }

    // ── Membership ────────────────────────────────────────────────────────────

    public void addParticipant(Long userId, Long conversationId, Long targetUserId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanManageConversation(userId, conversation);
        assertActiveUser(targetUserId);
        joinIfNeeded(conversation, targetUserId, ChatParticipantRole.MEMBER);
    }

    public void removeParticipant(Long userId, Long conversationId, Long targetUserId) {
        ChatConversation conversation = requireConversation(conversationId);
        if (!userId.equals(targetUserId)) {
            chatAuth.assertCanManageConversation(userId, conversation);
        }
        ChatParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        participant.setIsActive(false);
        participant.setLeftAt(LocalDateTime.now());
        participantRepository.save(participant);
    }

    private void joinIfNeeded(ChatConversation conversation, Long userId, ChatParticipantRole role) {
        ChatParticipant existing = participantRepository
                .findByConversationIdAndUserId(conversation.getId(), userId)
                .orElse(null);
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getIsActive())) {
                existing.setIsActive(true);
                existing.setLeftAt(null);
                participantRepository.save(existing);
            }
            return;
        }
        participantRepository.save(ChatParticipant.builder()
                .conversationId(conversation.getId())
                .userId(userId)
                .participantRole(role)
                .notificationLevel(ChatNotificationLevel.ALL)
                // Seed the watermark at the current tail so a new member does not see the entire
                // history as "unread" nor generate a receipt backfill for messages sent before
                // they joined.
                .lastReadMessageId(conversation.getLastMessageId())
                .lastDeliveredMessageId(conversation.getLastMessageId())
                .isActive(true)
                .joinedAt(LocalDateTime.now())
                .build());
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChatConversation getConversation(Long userId, Long conversationId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        return conversation;
    }

    @Transactional(readOnly = true)
    public List<ChatConversationSummaryDto> listMyConversations(Long userId) {
        List<Long> ids = participantRepository.findActiveConversationIdsByUserId(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<ChatConversationSummaryDto> result = new ArrayList<>();
        for (ChatConversation c : conversationRepository.findAllById(ids)) {
            if (Boolean.TRUE.equals(c.getIsArchived())) {
                continue;
            }
            ChatMessage last = messageRepository.findTopByConversationIdOrderByIdDesc(c.getId()).orElse(null);
            Long otherUserId = null;
            String title = c.getTitle();
            if (c.getConversationType() == ChatConversationType.DIRECT) {
                otherUserId = participantRepository.findByConversationIdAndIsActiveTrue(c.getId()).stream()
                        .map(ChatParticipant::getUserId)
                        .filter(id -> !id.equals(userId))
                        .findFirst()
                        .orElse(null);
                if (otherUserId != null) {
                    title = userRepository.findById(otherUserId)
                            .map(User::getName)
                            .orElse("User #" + otherUserId);
                }
            }
            result.add(ChatConversationSummaryDto.builder()
                    .id(c.getId())
                    .conversationType(c.getConversationType() != null ? c.getConversationType().name() : null)
                    .title(title)
                    .branchId(c.getBranchId())
                    .departmentId(c.getDepartmentId())
                    .memoId(c.getMemoId())
                    .otherUserId(otherUserId)
                    .lastMessageId(c.getLastMessageId())
                    .lastMessageAt(c.getLastMessageAt())
                    .lastMessagePreview(last != null && !Boolean.TRUE.equals(last.getIsDeleted())
                            ? preview(last.getMessage()) : null)
                    .unreadCount(unreadCount(c.getId(), userId))
                    .isLocked(c.getIsLocked())
                    .build());
        }
        result.sort(Comparator.comparing(ChatConversationSummaryDto::getLastMessageAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long conversationId, Long userId) {
        ChatParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);
        Long lastRead = participant != null ? participant.getLastReadMessageId() : null;
        if (lastRead == null) {
            return messageRepository.countByConversationIdAndSenderIdNot(conversationId, userId);
        }
        return messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(conversationId, lastRead, userId);
    }

    public void markRead(Long userId, Long conversationId, Long lastReadMessageId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        ChatParticipant participant = loadOrCreateParticipant(conversationId, userId);

        Long previousRead = participant.getLastReadMessageId();
        Long newRead = previousRead;
        if (lastReadMessageId != null && (previousRead == null || lastReadMessageId > previousRead)) {
            newRead = lastReadMessageId;
        }

        if (newRead != null && (previousRead == null || newRead > previousRead)) {
            materializeReceipts(conversation, userId, previousRead, newRead, true);
            participant.setLastReadMessageId(newRead);
        }
        // Reading implies delivery.
        participant.setLastDeliveredMessageId(max(participant.getLastDeliveredMessageId(), newRead));
        participant.setLastReadAt(LocalDateTime.now());
        participantRepository.save(participant);

        broadcastReceipt(conversation.getId(), participant.getUserId(),
                participant.getLastDeliveredMessageId(), participant.getLastReadMessageId());
    }

    /**
     * Marks every message up to the conversation's current tail as delivered to {@code userId}
     * (without implying they were read). Called when a client receives the conversation feed.
     */
    public void markDelivered(Long userId, Long conversationId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        Long upTo = conversation.getLastMessageId();
        if (upTo == null) {
            return;
        }
        ChatParticipant participant = loadOrCreateParticipant(conversationId, userId);
        Long previousDelivered = participant.getLastDeliveredMessageId();
        if (previousDelivered != null && upTo <= previousDelivered) {
            return;
        }
        materializeReceipts(conversation, userId, previousDelivered, upTo, false);
        participant.setLastDeliveredMessageId(upTo);
        participantRepository.save(participant);

        broadcastReceipt(conversation.getId(), participant.getUserId(),
                participant.getLastDeliveredMessageId(), participant.getLastReadMessageId());
    }

    private ChatParticipant loadOrCreateParticipant(Long conversationId, Long userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseGet(() -> participantRepository.save(ChatParticipant.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .participantRole(ChatParticipantRole.MEMBER)
                        .notificationLevel(ChatNotificationLevel.ALL)
                        .isActive(true)
                        .joinedAt(LocalDateTime.now())
                        .build()));
    }

    /**
     * Persists {@code chat_message_receipt} rows for messages in {@code (afterId, upToId]} authored
     * by someone other than {@code userId}. Bounded to DIRECT/GROUP because BRANCH/DEPARTMENT/MEMO
     * fan-out is tracked with watermarks only; a member's watermark is seeded at join time so this
     * is never a full-history backfill.
     */
    private void materializeReceipts(ChatConversation conversation, Long userId,
                                     Long afterId, Long upToId, boolean markRead) {
        if (upToId == null || conversation.getConversationType() == null
                || !RECEIPT_TYPES.contains(conversation.getConversationType())) {
            return;
        }
        long from = afterId == null ? 0L : afterId;
        List<ChatMessage> messages = messageRepository
                .findByConversationIdAndIdGreaterThanAndIdLessThanEqualOrderByIdAsc(conversation.getId(), from, upToId)
                .stream()
                .filter(m -> !userId.equals(m.getSenderId()))
                .toList();
        if (messages.isEmpty()) {
            return;
        }
        List<Long> ids = messages.stream().map(ChatMessage::getId).toList();
        Map<Long, ChatMessageReceipt> existing = new HashMap<>();
        for (ChatMessageReceipt r : receiptRepository.findByMessageIdInAndUserId(ids, userId)) {
            existing.put(r.getMessageId(), r);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ChatMessageReceipt> toSave = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            ChatMessageReceipt receipt = existing.get(m.getId());
            if (receipt == null) {
                receipt = new ChatMessageReceipt();
                receipt.setMessageId(m.getId());
                receipt.setUserId(userId);
            }
            if (receipt.getDeliveredAt() == null) {
                receipt.setDeliveredAt(now);
            }
            if (markRead && receipt.getReadAt() == null) {
                receipt.setReadAt(now);
            }
            toSave.add(receipt);
        }
        receiptRepository.saveAll(toSave);
    }

    private void broadcastReceipt(Long conversationId, Long userId, Long deliveredUpTo, Long readUpTo) {
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/receipts",
                ChatReceiptEventDto.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .deliveredUpTo(deliveredUpTo)
                        .readUpTo(readUpTo)
                        .at(LocalDateTime.now())
                        .build());
    }

    // ── Phase 2d: mark-unread ─────────────────────────────────────────────────

    /**
     * Rewinds the caller's read watermark so {@code messageId} (and anything newer) is counted
     * as unread again. Never moves the watermark forward.
     */
    public void markUnread(Long userId, Long conversationId, Long messageId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        ChatParticipant participant = loadOrCreateParticipant(conversationId, userId);
        long target = messageId == null ? 0L : Math.max(0L, messageId - 1);
        Long current = participant.getLastReadMessageId();
        if (current == null || target < current) {
            participant.setLastReadMessageId(target);
            participant.setLastReadAt(null);
            participantRepository.save(participant);
            broadcastReceipt(conversationId, userId, participant.getLastDeliveredMessageId(), target);
            log.debug("Conversation {} marked unread by userId={} (watermark rewound to {})",
                    conversationId, userId, target);
        }
    }

    // ── Phase 2h: per-conversation notification settings ─────────────────────

    public String getNotificationSetting(Long userId, Long conversationId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        ChatParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);
        ChatNotificationLevel level = participant != null && participant.getNotificationLevel() != null
                ? participant.getNotificationLevel() : ChatNotificationLevel.ALL;
        return level.name();
    }

    public String updateNotificationSetting(Long userId, Long conversationId, String level) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        ChatNotificationLevel parsed = parseNotificationLevel(level);
        ChatParticipant participant = loadOrCreateParticipant(conversationId, userId);
        participant.setNotificationLevel(parsed);
        participantRepository.save(participant);
        log.info("Conversation {} notification level set to {} by userId={}",
                conversationId, parsed, userId);
        return parsed.name();
    }

    private static ChatNotificationLevel parseNotificationLevel(String raw) {
        try {
            return ChatNotificationLevel.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported notification level: " + raw);
        }
    }

    // ── Phase 2j: moderation (lock) + members ────────────────────────────────

    /** Manager (or admin) locks/unlocks a conversation — locked = read-only for everyone. */
    public ChatConversation setLocked(Long userId, Long conversationId, Boolean locked) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanManageConversation(userId, conversation);
        conversation.setIsLocked(Boolean.TRUE.equals(locked));
        ChatConversation saved = conversationRepository.save(conversation);
        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId + "/meta",
                ChatConversationLockEventDto.builder()
                        .conversationId(conversationId)
                        .locked(saved.getIsLocked())
                        .at(LocalDateTime.now())
                        .build());
        log.info("Conversation {} locked={} by userId={}", conversationId, saved.getIsLocked(), userId);
        return saved;
    }

    /** Active members of a conversation with display names + live presence overlay. */
    @Transactional(readOnly = true)
    public List<ChatMemberDto> listMembers(Long userId, Long conversationId) {
        ChatConversation conversation = requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
        List<Long> ids = participants.stream().map(ChatParticipant::getUserId).distinct().toList();
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(u -> names.put(u.getId(), u.getName()));
        }
        ChatPresenceService presence = presenceProvider.getIfAvailable();
        List<ChatMemberDto> result = new ArrayList<>(participants.size());
        for (ChatParticipant p : participants) {
            boolean online = presence != null && presence.isOnline(p.getUserId());
            result.add(ChatMemberDto.builder()
                    .userId(p.getUserId())
                    .name(names.getOrDefault(p.getUserId(), "User #" + p.getUserId()))
                    .participantRole(p.getParticipantRole() != null ? p.getParticipantRole().name() : null)
                    .online(online)
                    .lastSeen(presence != null ? presence.lastSeen(p.getUserId()) : null)
                    .build());
        }
        result.sort(Comparator.comparing(ChatMemberDto::getName,
                Comparator.nullsLast(String::compareTo)));
        return result;
    }

    private static Long max(Long a, Long b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return Math.max(a, b);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChatConversation requireConversation(Long conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is required");
        }
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
    }

    private void assertActiveUser(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getStatus() != Status.ACTIVE) {
            throw new AccessDeniedException("Target user is not active");
        }
    }

    private static ChatConversationType parseType(String raw) {
        try {
            return ChatConversationType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported conversationType: " + raw);
        }
    }

    private static Long resolveMemoId(String dedupeKey) {
        // dedupeKey format: "MEMO:{id}"
        if (dedupeKey == null || !dedupeKey.startsWith("MEMO:")) {
            return null;
        }
        return Long.parseLong(dedupeKey.substring("MEMO:".length()));
    }

    private static String preview(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }
}
