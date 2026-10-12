package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

/**
 * A message saved for later by one user (Phase 2d bookmarking).
 *
 * Scoped to {@code user_id} — a user can only ever query their own bookmarks, and the unique
 * constraint {@code (user_id, message_id)} makes bookmarks idempotent.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_bookmark",
        uniqueConstraints = @UniqueConstraint(name = "uq_cbookmark_user_msg",
                columnNames = {"user_id", "message_id"}),
        indexes = {
                @Index(name = "idx_cbookmark_user", columnList = "user_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatBookmark extends BaseEntity<Long> implements Serializable {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "conversation_id")
    private Long conversationId;
}