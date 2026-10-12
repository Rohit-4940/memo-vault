package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Realtime delivery/read watermark update, broadcast on {@code /topic/conversation/{id}/receipts}.
 *
 * Receivers (message authors) use {@code deliveredUpTo}/{@code readUpTo} to move their own
 * messages to the DELIVERED/READ state without re-fetching history.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReceiptEventDto {
    private Long conversationId;
    /** The user whose watermark advanced. */
    private Long userId;
    private Long deliveredUpTo;
    private Long readUpTo;
    private LocalDateTime at;
}
