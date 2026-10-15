package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Assignment of a {@link ChatConversationLabel} to a conversation, scoped to the label's owner
 * (Enterprise Phase 5).
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_conversation_label_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uq_chat_label_conv", columnNames = {"label_id", "conversation_id"})
}, indexes = {
        @Index(name = "idx_cmsgass_label", columnList = "label_id"),
        @Index(name = "idx_cmsgass_conv", columnList = "conversation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatConversationLabelAssignment extends BaseEntity<Long> implements Serializable {

    @Column(name = "label_id", nullable = false)
    private Long labelId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** Denormalised owner (the label's user) for cheap access-scoped lookups. */
    @Column(name = "user_id", nullable = false)
    private Long userId;
}
