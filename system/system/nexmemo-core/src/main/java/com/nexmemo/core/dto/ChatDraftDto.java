package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * A caller's unsent draft for a conversation (Enterprise Phase 5). Private to its author.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatDraftDto {

    private Long conversationId;
    private String body;
    private LocalDateTime updatedAt;
}
