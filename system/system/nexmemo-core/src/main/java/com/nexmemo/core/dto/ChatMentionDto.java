package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * A single entry in the caller's "tagged" inbox.
 *
 * Only mentions addressed to the requesting user are ever returned (scoped by
 * {@code mentioned_user_id} = principal), so this DTO cannot be used to enumerate other users'
 * mentions.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMentionDto {
    private Long messageId;
    private Long conversationId;
    private Long mentionedUserId;
    private Long mentionedByUserId;
    private String mentionedByName;
    /** Mention tokens resolved to plain {@code @Name} text, truncated. */
    private String preview;
    private LocalDateTime createdDate;
}
