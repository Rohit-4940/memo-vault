package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * One entry in the caller's saved-messages list (Phase 2d bookmarking). Scoped to the principal.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatBookmarkDto {
    private Long messageId;
    private Long conversationId;
    private String conversationTitle;
    private Long senderId;
    private String senderName;
    private String preview;
    private LocalDateTime createdDate;
}