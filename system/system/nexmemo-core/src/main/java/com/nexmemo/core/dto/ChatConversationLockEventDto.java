package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Realtime lock-state transition, broadcast on {@code /topic/conversation/{id}/meta}
 * so every participant's UI can disable posting when an admin locks the conversation.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatConversationLockEventDto {
    private Long conversationId;
    private boolean locked;
    private LocalDateTime at;
}