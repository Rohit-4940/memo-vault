package com.nexmemo.core.dto;

import lombok.*;

/**
 * Realtime reaction change, broadcast on {@code /topic/conversation/{id}/reactions}.
 *
 * {@code count} is the authoritative total for the emoji after the change, so clients can set
 * the count directly instead of incrementing/decrementing (drift-free across missed messages).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReactionEventDto {
    private Long conversationId;
    private Long messageId;
    private String emoji;
    private Long userId;
    /** ADDED | REMOVED */
    private String action;
    private long count;
}
