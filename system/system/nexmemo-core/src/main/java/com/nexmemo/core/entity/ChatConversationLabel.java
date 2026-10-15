package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * A user-scoped label (the "mail-centre" folder equivalent) that can be applied to a caller's own
 * conversations (Enterprise Phase 5).
 *
 * <p>Labels are <b>private organisational overlays</b>: they never grant access to a conversation
 * and a foreign label id is treated as not found. Applying a label still requires access to the
 * underlying conversation.</p>
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_conversation_label", uniqueConstraints = {
        @UniqueConstraint(name = "uq_chat_label_user_name", columnNames = {"user_id", "name"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatConversationLabel extends BaseEntity<Long> implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String name;

    /** Optional display colour token (e.g. {@code #2563eb}); purely cosmetic. */
    @Column(length = 20)
    private String color;
}
