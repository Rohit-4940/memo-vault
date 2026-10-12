package com.nexmemo.core.entity;

import com.nexmemo.core.enums.ChatNotificationLevel;
import com.nexmemo.core.enums.ChatParticipantRole;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Membership row for a conversation.
 *
 * For DIRECT/GROUP conversations membership is authoritative. For BRANCH/DEPARTMENT/MEMO
 * conversations access is derived from the underlying resource, but a participant row is
 * still created on first join so unread tracking ({@code lastReadMessageId}) works uniformly.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_participant",
        uniqueConstraints = @UniqueConstraint(name = "uq_participant_conv_user",
                columnNames = {"conversation_id", "user_id"}),
        indexes = {
                @Index(name = "idx_cp_user_active", columnList = "user_id,is_active"),
                @Index(name = "idx_cp_conversation", columnList = "conversation_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatParticipant extends BaseEntity<Long> implements Serializable {

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "participant_role", nullable = false, length = 20)
    private ChatParticipantRole participantRole = ChatParticipantRole.MEMBER;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_level", nullable = false, length = 20)
    private ChatNotificationLevel notificationLevel = ChatNotificationLevel.ALL;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    /**
     * Delivery watermark. Every message with id &le; this value has been delivered to this
     * user. Read implies delivered, so {@code lastDeliveredMessageId >= lastReadMessageId}.
     */
    @Column(name = "last_delivered_message_id")
    private Long lastDeliveredMessageId;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;
}
