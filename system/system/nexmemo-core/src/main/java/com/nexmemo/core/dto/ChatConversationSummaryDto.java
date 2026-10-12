package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for a conversation in the user's list, with unread count and last-message preview.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationSummaryDto {
    private Long id;
    private String conversationType;
    private String title;
    private Long branchId;
    private Long departmentId;
    private Long memoId;
    /** For DIRECT conversations: the other participant (for avatars / display). */
    private Long otherUserId;
    private Long lastMessageId;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private long unreadCount;

    /** Locked conversations are read-only (send/edit/delete rejected server-side). */
    private Boolean isLocked;
}
