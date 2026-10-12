package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Immutable audit row capturing the content of a chat message immediately before an edit.
 *
 * We never overwrite history: each edit appends the previous body here so moderators / auditors
 * can reconstruct exactly what was said, even after the live message has been soft-deleted.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_message_edit_history",
        indexes = {
                @Index(name = "idx_cmeh_message", columnList = "message_id,edited_at"),
                @Index(name = "idx_cmeh_edited_by", columnList = "edited_by_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatMessageEditHistory extends BaseEntity<Long> implements Serializable {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /** The message body as it existed before this edit. */
    @Column(name = "previous_content", columnDefinition = "TEXT")
    private String previousContent;

    @Column(name = "edited_by_id")
    private Long editedById;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;
}
