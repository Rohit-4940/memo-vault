package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Realtime "someone is typing" indicator, broadcast on {@code /topic/conversation/{id}/typing}.
 * The client clears the indicator after a short timeout — the event carries no "stopped" state.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatTypingEventDto {
    private Long conversationId;
    private Long userId;
    private String displayName;
    private LocalDateTime at;
}