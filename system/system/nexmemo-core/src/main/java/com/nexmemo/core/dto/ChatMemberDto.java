package com.nexmemo.core.dto;

import lombok.*;

/**
 * A member of a conversation (identity only — role/name; no PII).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMemberDto {
    private Long userId;
    private String name;
    private String participantRole;
    private boolean online;
    private java.time.LocalDateTime lastSeen;
}