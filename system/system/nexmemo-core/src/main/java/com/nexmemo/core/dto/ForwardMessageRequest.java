package com.nexmemo.core.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request to forward a message into a target conversation (Phase 2e).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForwardMessageRequest {

    /** Conversation the message will be copied into. The caller must be able to post there. */
    @NotNull(message = "targetConversationId is required")
    private Long targetConversationId;

    /** Client-generated id so a retried forward is idempotent. */
    private String clientMessageId;
}