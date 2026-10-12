package com.nexmemo.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request to send a message. {@code clientMessageId} makes the operation idempotent:
 * retrying with the same id returns the already-persisted message instead of a duplicate.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "clientMessageId is required")
    @Size(max = 64, message = "clientMessageId must be at most 64 characters")
    private String clientMessageId;

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message exceeds maximum length of 5000")
    private String message;

    /** TEXT (default), SYSTEM, MEMO_REF, FILE. */
    private String messageType;

    /** Optional thread parent. */
    private Long parentMessageId;

    /** Optional mention targets (populated in Phase 2). */
    private List<Long> mentions;
}
