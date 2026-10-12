package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

/**
 * A single emoji reaction by one user on one message.
 *
 * Uniqueness is enforced at the database level on {@code (message_id, user_id, emoji)} so a user
 * cannot stack duplicate reactions and concurrent adds are idempotent.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_message_reaction",
        uniqueConstraints = @UniqueConstraint(name = "uq_cmreact_msg_user_emoji",
                columnNames = {"message_id", "user_id", "emoji"}),
        indexes = @Index(name = "idx_cmreact_message", columnList = "message_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatMessageReaction extends BaseEntity<Long> implements Serializable {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "emoji", nullable = false, length = 32)
    private String emoji;
}
