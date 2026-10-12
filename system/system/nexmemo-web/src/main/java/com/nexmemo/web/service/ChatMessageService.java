package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatAttachmentDto;
import com.nexmemo.core.dto.ChatMessageDto;
import com.nexmemo.core.dto.EditMessageRequest;
import com.nexmemo.core.dto.ForwardMessageRequest;
import com.nexmemo.core.dto.MessageStatusDto;
import com.nexmemo.core.dto.SendMessageRequest;
import com.nexmemo.core.entity.ChatAttachment;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageEditHistory;
import com.nexmemo.core.entity.ChatMessageMention;
import com.nexmemo.core.entity.ChatMessageReaction;
import com.nexmemo.core.entity.ChatMessageReceipt;
import com.nexmemo.core.entity.ChatParticipant;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.core.enums.ChatMessageType;
import com.nexmemo.security.repository.ChatAttachmentRepository;
import com.nexmemo.security.repository.ChatBookmarkRepository;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatMessageEditHistoryRepository;
import com.nexmemo.security.repository.ChatMessageMentionRepository;
import com.nexmemo.security.repository.ChatMessageReactionRepository;
import com.nexmemo.security.repository.ChatMessageReceiptRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Message send / history / edit / delete for the conversation model.
 *
 * Sends are idempotent on {@code (senderId, clientMessageId)} and every operation runs
 * through {@link ChatAuthorizationService#assertCanAccessConversation}. All responses are
 * mapped to {@link ChatMessageDto} so that soft-deleted content is never leaked to
 * non-administrators and delivery/read status is only exposed to the message author.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int MAX_PAGE_SIZE = 100;

    /** Authors may edit their own message for this long after sending (bank policy). */
    public static final Duration EDIT_WINDOW = Duration.ofMinutes(15);

    private final ChatMessageRepository messageRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatMessageReceiptRepository receiptRepository;
    private final ChatMessageEditHistoryRepository editHistoryRepository;
    private final ChatMessageReactionRepository reactionRepository;
    private final ChatMessageMentionRepository mentionRepository;
    private final ChatAttachmentRepository attachmentRepository;
    private final ChatBookmarkRepository bookmarkRepository;
    private final UserRepository userRepository;
    private final ChatConversationService conversationService;
    private final ChatMentionService mentionService;
    private final ChatAuthorizationService chatAuth;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Send ──────────────────────────────────────────────────────────────────

    public ChatMessageDto send(Long userId, Long conversationId, SendMessageRequest req) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        assertNotLocked(conversation);

        // Idempotency: a retry with the same client id returns the stored message.
        if (req.getClientMessageId() != null && !req.getClientMessageId().isBlank()) {
            Optional<ChatMessage> existing = messageRepository
                    .findBySenderIdAndClientMessageId(userId, req.getClientMessageId());
            if (existing.isPresent()) {
                return singleDto(userId, existing.get());
            }
        }

        ChatMessage parent = null;
        if (req.getParentMessageId() != null) {
            parent = messageRepository.findById(req.getParentMessageId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent message not found"));
            if (!conversationId.equals(parent.getConversationId())) {
                throw new IllegalArgumentException("Parent message belongs to a different conversation");
            }
        }

        ChatMessageType type = parseType(req.getMessageType());

        ChatMessage message = ChatMessage.builder()
                .senderId(userId)
                .conversationId(conversationId)
                .conversationType(conversation.getConversationType().name())
                .branchId(conversation.getBranchId())
                .memoId(conversation.getMemoId())
                .receiverId(resolveDirectReceiver(conversation, userId))
                .clientMessageId(req.getClientMessageId())
                .messageType(type)
                .message(req.getMessage().trim())
                .parentMessageId(req.getParentMessageId())
                .replyCount(0)
                .isRead(false)
                .isEdited(false)
                .isDeleted(false)
                .build();

        ChatMessage saved = messageRepository.save(message);

        conversation.setLastMessageId(saved.getId());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        if (parent != null) {
            parent.setReplyCount((parent.getReplyCount() == null ? 0 : parent.getReplyCount()) + 1);
            messageRepository.save(parent);
        }

        // The sender has implicitly read their own message.
        conversationService.markRead(userId, conversationId, saved.getId());

        // Resolve/persist @mentions and notify newly tagged users (authorized targets only).
        mentionService.syncMentions(conversation, saved, userId);

        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
        ChatMessageDto dto = toDto(userId, saved, chatAuth.isAdmin(userId), participants,
                reactionsFor(saved.getId()), mentionsFor(saved.getId()),
                attachmentsFor(saved.getId()), isBookmarked(userId, saved.getId()));
        broadcast(conversation, dto, participants);
        log.debug("Message {} sent to conversation {} by userId={}", saved.getId(), conversationId, userId);
        return dto;
    }

    // ── History / lookup ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ChatMessageDto> getHistory(Long userId, Long conversationId, Long beforeId, int size) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        PageRequest pageable = PageRequest.of(0, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Page<ChatMessage> page;
        if (beforeId == null) {
            page = messageRepository.findByConversationIdAndIsDeletedFalseOrderByIdDesc(conversationId, pageable);
        } else {
            page = messageRepository.findByConversationIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(
                    conversationId, beforeId, pageable);
        }
        return new PageImpl<>(renderBulk(userId, page.getContent()), pageable, page.getTotalElements());
    }

    // ── Phase 2c: threads ─────────────────────────────────────────────────────

    /**
     * Replies to a parent message, oldest-first. The parent may already be deleted — its
     * replies are still visible (the parent body itself is tombstoned, not the thread).
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> getReplies(Long userId, Long conversationId, Long parentId,
                                           Long afterId, int size) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        ChatMessage parent = requireConversationMessage(conversationId, parentId);
        if (parent.getId() == null) {
            throw new IllegalArgumentException("Parent message not found");
        }
        PageRequest pageable = PageRequest.of(0, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        List<ChatMessage> replies = afterId == null
                ? messageRepository.findByParentMessageIdAndIsDeletedFalseOrderByIdAsc(parent.getId(), pageable)
                : messageRepository.findByParentMessageIdAndIsDeletedFalseAndIdGreaterThanOrderByIdAsc(
                        parent.getId(), afterId, pageable);
        return renderBulk(userId, replies);
    }

    // ── Phase 2d: pinned ──────────────────────────────────────────────────────

    /** Pin list for the "pinned" view of a conversation (author/admin pins only). */
    @Transactional(readOnly = true)
    public Page<ChatMessageDto> getPinned(Long userId, Long conversationId, int size) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        PageRequest pageable = PageRequest.of(0, Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ChatMessage> page =
                messageRepository.findByConversationIdAndIsPinnedTrueAndIsDeletedFalseOrderByIdDesc(
                        conversationId, pageable);
        return new PageImpl<>(renderBulk(userId, page.getContent()), pageable, page.getTotalElements());
    }

    /** Author (or admin) pins/unpins a message. Idempotent — pinning twice unpins. */
    public ChatMessageDto pinMessage(Long userId, Long conversationId, Long messageId) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        ChatMessage message = requireConversationMessage(conversationId, messageId);
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new IllegalArgumentException("A deleted message cannot be pinned");
        }
        if (!userId.equals(message.getSenderId()) && !chatAuth.isAdmin(userId)) {
            throw new AccessDeniedException("Only the author or an administrator can pin a message");
        }
        message.setIsPinned(!Boolean.TRUE.equals(message.getIsPinned()));
        ChatMessage saved = messageRepository.save(message);

        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
        ChatMessageDto dto = toDto(userId, saved, chatAuth.isAdmin(userId), participants,
                reactionsFor(saved.getId()), mentionsFor(saved.getId()),
                attachmentsFor(saved.getId()), isBookmarked(userId, saved.getId()));
        broadcast(conversation, dto, participants);
        log.info("Message {} pinned={} by userId={} in conversation {}",
                messageId, saved.getIsPinned(), userId, conversationId);
        return dto;
    }

    // ── Phase 2e: forwarding ──────────────────────────────────────────────────

    /**
     * Copies a message into a target conversation. The caller must be able to read the source
     * conversation and post into the target. The copy re-runs mention parsing so tags are only
     * persisted when the tagged user can actually access the target conversation.
     */
    public ChatMessageDto forwardMessage(Long userId, Long sourceMessageId, ForwardMessageRequest req) {
        ChatMessage source = messageRepository.findById(sourceMessageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (source.getConversationId() == null) {
            throw new IllegalArgumentException("Only conversation messages can be forwarded");
        }
        if (Boolean.TRUE.equals(source.getIsDeleted())) {
            throw new IllegalArgumentException("A deleted message cannot be forwarded");
        }
        // 1. Read authorization on the source.
        ChatConversation sourceConversation = conversationService.requireConversation(source.getConversationId());
        chatAuth.assertCanAccessConversation(userId, sourceConversation);

        // 2. Write authorization on the target + locked check.
        ChatConversation target = conversationService.requireConversation(req.getTargetConversationId());
        chatAuth.assertCanAccessConversation(userId, target);
        assertNotLocked(target);

        // Idempotency on the caller's client id.
        if (req.getClientMessageId() != null && !req.getClientMessageId().isBlank()) {
            Optional<ChatMessage> existing = messageRepository
                    .findBySenderIdAndClientMessageId(userId, req.getClientMessageId());
            if (existing.isPresent()) {
                return singleDto(userId, existing.get());
            }
        }

        String original = source.getMessage();
        String senderName = userRepository.findById(source.getSenderId())
                .map(u -> u.getName())
                .orElse("User #" + source.getSenderId());
        String body = "↪ Forwarded from " + senderName + ":\n" + (original == null ? "" : original);
        ChatMessage forwarded = ChatMessage.builder()
                .senderId(userId)
                .conversationId(target.getId())
                .conversationType(target.getConversationType().name())
                .branchId(target.getBranchId())
                .memoId(target.getMemoId())
                .receiverId(resolveDirectReceiver(target, userId))
                .clientMessageId(req.getClientMessageId())
                .messageType(ChatMessageType.FORWARD)
                .message(body)
                .replyCount(0)
                .isRead(false)
                .isEdited(false)
                .isDeleted(false)
                .build();
        ChatMessage saved = messageRepository.save(forwarded);

        target.setLastMessageId(saved.getId());
        target.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(target);

        conversationService.markRead(userId, target.getId(), saved.getId());

        mentionService.syncMentions(target, saved, userId);

        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(target.getId());
        ChatMessageDto dto = toDto(userId, saved, chatAuth.isAdmin(userId), participants,
                reactionsFor(saved.getId()), mentionsFor(saved.getId()),
                attachmentsFor(saved.getId()), isBookmarked(userId, saved.getId()));
        broadcast(target, dto, participants);
        log.info("Message {} forwarded by userId={} from conversation {} to conversation {}",
                sourceMessageId, userId, source.getConversationId(), target.getId());
        return dto;
    }

    // ── Phase 2f: attachment updates ──────────────────────────────────────────

    /** Re-broadcasts a message after an attachment was added, so peers see the new file. */
    public ChatMessageDto broadcastMessageAfterAttachment(Long userId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (message.getConversationId() == null) {
            throw new IllegalArgumentException("Only conversation messages can have attachments");
        }
        ChatConversation conversation = conversationService.requireConversation(message.getConversationId());
        chatAuth.assertCanAccessConversation(userId, conversation);
        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversation.getId());
        ChatMessageDto dto = toDto(userId, message, chatAuth.isAdmin(userId), participants,
                reactionsFor(message.getId()), mentionsFor(message.getId()),
                attachmentsFor(message.getId()), isBookmarked(userId, message.getId()));
        broadcast(conversation, dto, participants);
        return dto;
    }

    /** Public bulk renderer used by search (authorized conversation scope already applied). */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> renderMessages(Long userId, List<ChatMessage> messages) {
        return renderBulk(userId, messages);
    }

    @Transactional(readOnly = true)
    public ChatMessageDto getMessage(Long userId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (message.getConversationId() != null) {
            ChatConversation conversation = conversationService.requireConversation(message.getConversationId());
            chatAuth.assertCanAccessConversation(userId, conversation);
        } else {
            // Legacy standalone message (no conversation): fall back to the legacy check.
            chatAuth.assertCanAcknowledgeMessage(userId, message);
        }
        return singleDto(userId, message);
    }

    /**
     * Delivery/read status for a single message. Only the author (or an admin) may query it:
     * exposing who read a message to arbitrary participants would leak activity metadata.
     */
    @Transactional(readOnly = true)
    public MessageStatusDto getMessageStatus(Long userId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (message.getConversationId() == null) {
            throw new IllegalArgumentException("Status is only available for conversation messages");
        }
        ChatConversation conversation = conversationService.requireConversation(message.getConversationId());
        chatAuth.assertCanAccessConversation(userId, conversation);
        if (!userId.equals(message.getSenderId()) && !chatAuth.isAdmin(userId)) {
            throw new AccessDeniedException("Only the sender can view message status");
        }
        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversation.getId());
        return buildStatus(message, participants);
    }

    // ── Edit / delete ─────────────────────────────────────────────────────────

    public ChatMessageDto editMessage(Long userId, Long conversationId, Long messageId, EditMessageRequest req) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        assertNotLocked(conversation);

        ChatMessage message = requireConversationMessage(conversationId, messageId);
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new IllegalArgumentException("A deleted message cannot be edited");
        }
        if (!userId.equals(message.getSenderId())) {
            throw new AccessDeniedException("Only the author can edit a message");
        }
        if (isEditWindowClosed(message)) {
            throw new AccessDeniedException("The edit window (" + EDIT_WINDOW.toMinutes() + " minutes) has passed");
        }

        String newContent = req.getMessage().trim();
        if (newContent.isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }

        // Append an immutable audit row of the previous body before overwriting.
        editHistoryRepository.save(ChatMessageEditHistory.builder()
                .messageId(message.getId())
                .previousContent(message.getMessage())
                .editedById(userId)
                .editedAt(LocalDateTime.now())
                .build());

        message.setMessage(newContent);
        message.setIsEdited(true);
        message.setEditedAt(LocalDateTime.now());
        ChatMessage saved = messageRepository.save(message);

        // Re-parse mentions: drop removed tags, keep existing, notify only newly tagged users.
        mentionService.syncMentions(conversation, saved, userId);

        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
        ChatMessageDto dto = toDto(userId, saved, chatAuth.isAdmin(userId), participants,
                reactionsFor(saved.getId()), mentionsFor(saved.getId()),
                attachmentsFor(saved.getId()), isBookmarked(userId, saved.getId()));
        broadcast(conversation, dto, participants);
        log.info("Message {} edited by userId={} in conversation {}", messageId, userId, conversationId);
        return dto;
    }

    public ChatMessageDto deleteMessage(Long userId, Long conversationId, Long messageId, String reason) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        assertNotLocked(conversation);

        ChatMessage message = requireConversationMessage(conversationId, messageId);
        boolean isAuthor = userId.equals(message.getSenderId());
        boolean isAdmin = chatAuth.isAdmin(userId);
        if (!isAuthor && !isAdmin) {
            throw new AccessDeniedException("Only the author or an administrator can delete a message");
        }
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            return singleDto(userId, message); // already deleted — idempotent
        }

        message.setIsDeleted(true);
        message.setDeletedById(userId);
        message.setDeletedReason(reason == null || reason.isBlank() ? "deleted" : reason.trim());
        ChatMessage saved = messageRepository.save(message);

        // Do not retain references to a deleted message's tags.
        mentionService.removeForMessage(saved.getId());

        // Broadcast the tombstone. Content is hidden from non-admins by the mapper.
        List<ChatParticipant> participants =
                participantRepository.findByConversationIdAndIsActiveTrue(conversationId);
        ChatMessageDto dto = toDto(userId, saved, chatAuth.isAdmin(userId), participants,
                reactionsFor(saved.getId()), List.of(),
                attachmentsFor(saved.getId()), isBookmarked(userId, saved.getId()));
        broadcast(conversation, dto, participants);
        log.info("Message {} soft-deleted by userId={} (author={}, admin={}) in conversation {}",
                messageId, userId, isAuthor, isAdmin, conversationId);
        return dto;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /** Loads participants + reactions + mentions + attachments for a single message and maps it. */
    private ChatMessageDto singleDto(Long viewerId, ChatMessage m) {
        List<ChatParticipant> participants = m.getConversationId() != null
                ? participantRepository.findByConversationIdAndIsActiveTrue(m.getConversationId())
                : List.of();
        return toDto(viewerId, m, chatAuth.isAdmin(viewerId), participants,
                reactionsFor(m.getId()), mentionsFor(m.getId()),
                attachmentsFor(m.getId()), isBookmarked(viewerId, m.getId()));
    }

    /** Bulk renderer: shared by history, threads and search (single participant/reaction pass). */
    private List<ChatMessageDto> renderBulk(Long viewerId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        boolean viewerIsAdmin = chatAuth.isAdmin(viewerId);
        List<Long> ids = messages.stream().map(ChatMessage::getId).toList();
        Map<Long, List<ChatMessageReaction>> reactions =
                ChatReactionService.groupByMessage(reactionRepository.findByMessageIdIn(ids));
        Map<Long, List<Long>> mentions =
                ChatMentionService.groupByMessage(mentionRepository.findByMessageIdIn(ids));
        Map<Long, List<ChatAttachmentDto>> attachments =
                ChatAttachmentService.groupByMessage(attachmentRepository.findByMessageIdInOrderByIdAsc(ids));
        Set<Long> bookmarked = bookmarkMessageIds(viewerId, ids);

        List<Long> conversationIds = messages.stream()
                .map(ChatMessage::getConversationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<ChatParticipant>> participantsByConversation = new LinkedHashMap<>();
        if (!conversationIds.isEmpty()) {
            for (ChatParticipant p :
                    participantRepository.findByConversationIdInAndIsActiveTrue(conversationIds)) {
                participantsByConversation
                        .computeIfAbsent(p.getConversationId(), k -> new ArrayList<>())
                        .add(p);
            }
        }

        List<ChatMessageDto> out = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            out.add(toDto(viewerId, m, viewerIsAdmin,
                    participantsByConversation.getOrDefault(m.getConversationId(), List.of()),
                    reactions.getOrDefault(m.getId(), List.of()),
                    mentions.getOrDefault(m.getId(), List.of()),
                    attachments.getOrDefault(m.getId(), List.of()),
                    bookmarked.contains(m.getId())));
        }
        return out;
    }

    private List<ChatMessageReaction> reactionsFor(Long messageId) {
        return reactionRepository.findByMessageIdOrderByCreatedDateAsc(messageId);
    }

    private List<Long> mentionsFor(Long messageId) {
        return mentionRepository.findByMessageId(messageId).stream()
                .map(ChatMessageMention::getMentionedUserId)
                .toList();
    }

    private List<ChatAttachmentDto> attachmentsFor(Long messageId) {
        return ChatAttachmentService.groupByMessage(
                        attachmentRepository.findByMessageIdInOrderByIdAsc(List.of(messageId)))
                .getOrDefault(messageId, List.of());
    }

    private boolean isBookmarked(Long viewerId, Long messageId) {
        return bookmarkMessageIds(viewerId, List.of(messageId)).contains(messageId);
    }

    private Set<Long> bookmarkMessageIds(Long viewerId, List<Long> messageIds) {
        if (viewerId == null || messageIds == null || messageIds.isEmpty()) {
            return Set.of();
        }
        List<com.nexmemo.core.entity.ChatBookmark> rows =
                bookmarkRepository.findByUserIdAndMessageIdIn(viewerId, messageIds);
        if (rows == null || rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(b -> b.getMessageId()).collect(Collectors.toSet());
    }

    private ChatMessageDto toDto(Long viewerId, ChatMessage m, boolean viewerIsAdmin,
                                 List<ChatParticipant> participants, List<ChatMessageReaction> reactions,
                                 List<Long> mentionedUserIds,
                                 List<ChatAttachmentDto> attachments, boolean bookmarked) {
        boolean deleted = Boolean.TRUE.equals(m.getIsDeleted());
        ChatMessageDto.ChatMessageDtoBuilder builder = ChatMessageDto.builder()
                .id(m.getId())
                .conversationId(m.getConversationId())
                .conversationType(m.getConversationType())
                .senderId(m.getSenderId())
                .receiverId(m.getReceiverId())
                .memoId(m.getMemoId())
                .branchId(m.getBranchId())
                .messageType(m.getMessageType() != null ? m.getMessageType().name() : null)
                .message(deleted && !viewerIsAdmin ? null : m.getMessage())
                .parentMessageId(m.getParentMessageId())
                .replyCount(m.getReplyCount())
                .isEdited(m.getIsEdited())
                .editedAt(m.getEditedAt())
                .isDeleted(m.getIsDeleted())
                .isPinned(Boolean.TRUE.equals(m.getIsPinned()))
                .isBookmarked(bookmarked)
                .createdDate(m.getCreatedDate())
                .clientMessageId(m.getClientMessageId())
                .reactions(ChatReactionService.summarize(reactions, viewerId))
                .mentionedUserIds(mentionedUserIds == null ? List.of() : mentionedUserIds)
                .attachments(attachments == null ? List.of() : attachments);

        if (viewerIsAdmin) {
            builder.deletedReason(m.getDeletedReason());
        }

        ChatMessageDto dto = builder.build();

        // Only the author (or an admin) sees who has delivered/read the message.
        if (m.getConversationId() != null
                && (viewerId.equals(m.getSenderId()) || viewerIsAdmin)
                && !participants.isEmpty()) {
            MessageStatusDto status = buildStatus(m, participants);
            dto.setDeliveryState(status.getDeliveryState());
            dto.setDeliveredCount(status.getDeliveredCount());
            dto.setReadCount(status.getReadCount());
            dto.setTotalRecipients(status.getTotalRecipients());
            dto.setReadAt(status.getReadAt());
        }
        return dto;
    }

    private MessageStatusDto buildStatus(ChatMessage message, List<ChatParticipant> participants) {
        int delivered = 0;
        int read = 0;
        int recipients = 0;
        LocalDateTime readAt = null;
        for (ChatParticipant p : participants) {
            if (p.getUserId().equals(message.getSenderId())) {
                continue;
            }
            recipients++;
            Long deliveredUpTo = p.getLastDeliveredMessageId();
            Long readUpTo = p.getLastReadMessageId();
            if (deliveredUpTo != null && deliveredUpTo >= message.getId()) {
                delivered++;
            }
            if (readUpTo != null && readUpTo >= message.getId()) {
                read++;
            }
        }
        // Exact read timestamp is materialized for DIRECT conversations only.
        if (message.getConversationType() != null
                && ChatConversationType.DIRECT.name().equals(message.getConversationType())) {
            readAt = receiptRepository.findByMessageId(message.getId()).stream()
                    .map(ChatMessageReceipt::getReadAt)
                    .filter(java.util.Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }

        String state;
        if (recipients == 0) {
            state = "SENT";
        } else if (read == recipients) {
            state = "READ";
        } else if (delivered == recipients) {
            state = "DELIVERED";
        } else {
            state = "SENT";
        }

        return MessageStatusDto.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .deliveryState(state)
                .deliveredCount(delivered)
                .readCount(read)
                .totalRecipients(recipients)
                .readAt(readAt)
                .build();
    }

    // ── realtime ──────────────────────────────────────────────────────────────

    private void broadcast(ChatConversation conversation, ChatMessageDto dto,
                           List<ChatParticipant> participants) {
        // Generic conversation topic (SUBSCRIBE authorized by membership).
        messagingTemplate.convertAndSend("/topic/conversation/" + conversation.getId(), dto);

        // Per-user queues: each active participant gets the message addressed to them.
        for (ChatParticipant participant : participants) {
            messagingTemplate.convertAndSendToUser(participant.getUserId().toString(), "/queue/messages", dto);
        }

        // Backward compatibility: the legacy floating widget listens on the branch topic.
        if (conversation.getConversationType() == ChatConversationType.BRANCH && conversation.getBranchId() != null) {
            messagingTemplate.convertAndSend("/topic/branch/" + conversation.getBranchId(), dto);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatMessage requireConversationMessage(Long conversationId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!conversationId.equals(message.getConversationId())) {
            throw new IllegalArgumentException("Message does not belong to this conversation");
        }
        return message;
    }

    private boolean isEditWindowClosed(ChatMessage message) {
        LocalDateTime created = message.getCreatedDate();
        if (created == null) {
            return false;
        }
        return created.plus(EDIT_WINDOW).isBefore(LocalDateTime.now());
    }

    private void assertNotLocked(ChatConversation conversation) {
        if (Boolean.TRUE.equals(conversation.getIsLocked())) {
            throw new AccessDeniedException("This conversation is locked");
        }
    }

    private Long resolveDirectReceiver(ChatConversation conversation, Long senderId) {
        if (conversation.getConversationType() != ChatConversationType.DIRECT) {
            return null;
        }
        // receiverId is retained for compatibility with the legacy direct-message unread query.
        return participantRepository.findByConversationIdAndIsActiveTrue(conversation.getId()).stream()
                .map(ChatParticipant::getUserId)
                .filter(id -> !id.equals(senderId))
                .findFirst()
                .orElse(null);
    }

    private static ChatMessageType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChatMessageType.TEXT;
        }
        try {
            return ChatMessageType.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unsupported messageType: " + raw);
        }
    }
}
