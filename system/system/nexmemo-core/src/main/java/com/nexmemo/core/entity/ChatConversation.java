package com.nexmemo.core.entity;

import com.nexmemo.core.enums.ChatConversationType;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A chat conversation: DIRECT, GROUP, BRANCH, DEPARTMENT or MEMO.
 *
 * Resource columns (branchId/departmentId/memoId) are stored as raw ids so authorization
 * can be evaluated cheaply without triggering lazy loads. {@code dedupeKey} makes
 * get-or-create idempotent for every non-GROUP type.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_conversation", indexes = {
        @Index(name = "idx_conv_type_memo", columnList = "conversation_type,memo_id"),
        @Index(name = "idx_conv_branch", columnList = "branch_id"),
        @Index(name = "idx_conv_dept", columnList = "department_id"),
        @Index(name = "idx_conv_last_msg", columnList = "last_message_at"),
        @Index(name = "idx_conv_dedupe", columnList = "dedupe_key")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatConversation extends BaseEntity<Long> implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", nullable = false, length = 30)
    private ChatConversationType conversationType;

    @Column(length = 255)
    private String title;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "memo_id")
    private Long memoId;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Builder.Default
    @Column(name = "is_archived", nullable = false)
    private Boolean isArchived = false;

    @Builder.Default
    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Column(name = "last_message_id")
    private Long lastMessageId;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;
}
