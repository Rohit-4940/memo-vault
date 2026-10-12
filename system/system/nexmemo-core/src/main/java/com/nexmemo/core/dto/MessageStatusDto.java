package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Delivery/read status of a single message, computed from participant watermarks.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageStatusDto {
    private Long messageId;
    private Long conversationId;
    /** SENT (nobody) | DELIVERED (all delivered, not all read) | READ (all read). */
    private String deliveryState;
    private int deliveredCount;
    private int readCount;
    private int totalRecipients;
    /** For DIRECT conversations: when the peer read the message (null otherwise). */
    private LocalDateTime readAt;
}
