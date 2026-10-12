package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

/**
 * A single @mention: one message tagging one user.
 *
 * Uniqueness is enforced at the database level on {@code (message_id, mentioned_user_id)} so a user
 * cannot be tagged twice on the same message and re-parsing on edit is idempotent.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_message_mention",
        uniqueConstraints = @UniqueConstraint(name = "uq_cmment_msg_user",
                columnNames = {"message_id", "mentioned_user_id"}),
        indexes = {
                @Index(name = "idx_cmment_message", columnList = "message_id"),
                @Index(name = "idx_cmment_user", columnList = "mentioned_user_id, id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatMessageMention extends BaseEntity<Long> implements Serializable {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** Denormalized for scoped (per-conversation) queries. */
    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "mentioned_user_id", nullable = false)
    private Long mentionedUserId;
}
