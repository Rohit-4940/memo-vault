package com.nexmemo.core.dto;

import lombok.*;

/**
 * Realtime @mention notification, broadcast on {@code /topic/notifications/{mentionedUserId}}.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMentionEventDto {
    private Long conversationId;
    private Long messageId;
    private Long mentionedUserId;
    private Long mentionedByUserId;
    private String mentionedByName;
    /** Sanitized, truncated excerpt of the mentioning message. */
    private String preview;
}
