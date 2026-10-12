package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderId;

    private Long receiverId;

    private Long memoId;

    private Long branchId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(length = 30)
    private String conversationType;

    /** Client-generated id used to make sends idempotent (retry-safe). */
    @Column(name = "client_message_id", length = 64)
    private String clientMessageId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 20)
    private com.nexmemo.core.enums.ChatMessageType messageType = com.nexmemo.core.enums.ChatMessageType.TEXT;

    @Column(name = "parent_message_id")
    private Long parentMessageId;

    @Builder.Default
    @Column(name = "reply_count")
    private Integer replyCount = 0;

    @Builder.Default
    @Column(name = "is_edited")
    private Boolean isEdited = false;

    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    @Builder.Default
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;

    @Column(name = "deleted_by_id")
    private Long deletedById;

    @Column(name = "deleted_reason", length = 255)
    private String deletedReason;

    /** Pinned by the author or an administrator (Phase 2d). */
    @Builder.Default
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Legacy single flag. Superseded by chat_message_receipt for group correctness. */
    @Builder.Default
    @Column(nullable = false)
    private Boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void prePersist() { this.createdDate = LocalDateTime.now(); }
}
