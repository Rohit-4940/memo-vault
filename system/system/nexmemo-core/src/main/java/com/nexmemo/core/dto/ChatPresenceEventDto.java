package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Realtime presence transition, broadcast on {@code /topic/conversation/{id}/presence}.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatPresenceEventDto {
    private Long userId;
    private boolean online;
    private LocalDateTime at;
}