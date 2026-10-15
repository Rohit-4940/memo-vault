package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Per-user, per-conversation message draft (Enterprise Phase 5 "draft autosave").
 *
 * <p>Drafts are private to their author and are never broadcast. A row is created/updated as the
 * client autosaves and is deleted the moment the message is actually sent.</p>
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_draft", uniqueConstraints = {
        @UniqueConstraint(name = "uq_chat_draft_conv_user", columnNames = {"conversation_id", "user_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatDraft extends BaseEntity<Long> implements Serializable {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;
}
