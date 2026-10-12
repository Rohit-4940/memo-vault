package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * A flagged message in the admin moderation queue (Phase 2j). Admin-only endpoint.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReportDto {
    private Long messageId;
    private Long conversationId;
    private Long branchId;
    private Long senderId;
    private String senderName;
    private String preview;
    private LocalDateTime createdDate;
}