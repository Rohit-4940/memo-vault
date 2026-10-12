package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for a chat message.
 *
 * SECURITY: deleted messages are returned as tombstones — {@code message} is nulled unless the
 * viewer is an administrator, so a soft-deleted (moderated) body is never leaked to normal users.
 * Receipt counters are only populated for the viewer's own messages.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {

    private Long id;
    private Long conversationId;
    private String conversationType;
    private Long senderId;
    private Long receiverId;
    private Long memoId;
    private Long branchId;

    private String messageType;
    private String message;

    private Long parentMessageId;
    private Integer replyCount;

    private Boolean isEdited;
    private LocalDateTime editedAt;
    private Boolean isDeleted;
    private String deletedReason;

    /** Pinned by the author or an administrator (Phase 2d). */
    private Boolean isPinned;
    /** True when the viewer has saved this message (Phase 2d). */
    private Boolean isBookmarked;

    private LocalDateTime createdDate;
    private String clientMessageId;

    // ── reactions ─────────────────────────────────────────────────────────────
    private java.util.List<ChatReactionSummaryDto> reactions;

    // ── mentions ──────────────────────────────────────────────────────────────
    /** Users tagged via {@code @[name](id)} tokens. */
    private java.util.List<Long> mentionedUserIds;

    // ── attachments (Phase 2f) ────────────────────────────────────────────────
    private java.util.List<ChatAttachmentDto> attachments;

    // ── delivery/read status (sender's own messages only) ─────────────────────
    /** SENT | DELIVERED | READ — null when the viewer is not the author. */
    private String deliveryState;
    private Integer deliveredCount;
    private Integer readCount;
    private Integer totalRecipients;
    private LocalDateTime readAt;
}
