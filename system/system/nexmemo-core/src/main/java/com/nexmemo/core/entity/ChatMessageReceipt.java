package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Per-recipient delivery/read receipt for a message.
 *
 * Replaces the legacy single global {@code chat_message.is_read} flag, which produced
 * incorrect unread counts for group/branch conversations.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_message_receipt",
        uniqueConstraints = @UniqueConstraint(name = "uq_receipt_message_user",
                columnNames = {"message_id", "user_id"}),
        indexes = @Index(name = "idx_cmr_user_read", columnList = "user_id,read_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatMessageReceipt extends BaseEntity<Long> implements Serializable {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;
}
