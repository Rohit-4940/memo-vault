package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Presence of one user in a conversation (Phase 2g).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatPresenceDto {
    private Long userId;
    private String displayName;
    private boolean online;
    private LocalDateTime lastSeen;
}