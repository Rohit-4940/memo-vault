package com.nexmemo.core.entity;

import com.nexmemo.core.enums.ChatAttachmentScanStatus;
import com.nexmemo.core.enums.ChatMessageType;
import com.nexmemo.core.enums.ChatNotificationLevel;
import com.nexmemo.core.enums.ChatParticipantRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the Lombok {@code @Builder} + field-initializer trap.
 *
 * <p>Lombok's {@code @Builder} ignores field initializers unless the field is annotated
 * {@code @Builder.Default}. Because these entities are inserted with columns that are
 * {@code NOT NULL}, an unset default (null) caused runtime insert failures
 * (e.g. {@code chat_message.is_pinned}). These assertions fail loudly if a default is dropped.</p>
 */
class ChatEntityBuilderDefaultsTest {

    @Test
    void chatMessageBuilderAppliesNotNullDefaults() {
        ChatMessage m = ChatMessage.builder().senderId(1L).message("hi").build();

        assertThat(m.getIsPinned()).isFalse();
        assertThat(m.getIsRead()).isFalse();
        assertThat(m.getIsEdited()).isFalse();
        assertThat(m.getIsDeleted()).isFalse();
        assertThat(m.getReplyCount()).isZero();
        assertThat(m.getMessageType()).isEqualTo(ChatMessageType.TEXT);
    }

    @Test
    void chatConversationBuilderAppliesNotNullDefaults() {
        ChatConversation c = ChatConversation.builder().build();

        assertThat(c.getIsArchived()).isFalse();
        assertThat(c.getIsLocked()).isFalse();
    }

    @Test
    void chatParticipantBuilderAppliesNotNullDefaults() {
        ChatParticipant p = ChatParticipant.builder().conversationId(1L).userId(2L).build();

        assertThat(p.getParticipantRole()).isEqualTo(ChatParticipantRole.MEMBER);
        assertThat(p.getNotificationLevel()).isEqualTo(ChatNotificationLevel.ALL);
        assertThat(p.getIsActive()).isTrue();
    }

    @Test
    void chatAttachmentBuilderAppliesNotNullDefaults() {
        ChatAttachment a = ChatAttachment.builder().messageId(1L).conversationId(1L).build();

        assertThat(a.getScanStatus()).isEqualTo(ChatAttachmentScanStatus.PENDING);
    }

    @Test
    void notificationBuilderAppliesNotNullDefaults() {
        Notification n = Notification.builder().userId(1L).title("t").type("MEMO").build();

        assertThat(n.getIsRead()).isFalse();
    }

    @Test
    void auditLogBuilderAppliesNotNullDefaults() {
        AuditLog a = AuditLog.builder().action("TEST").build();

        assertThat(a.getStatus()).isEqualTo(AuditLog.AuditStatus.SUCCESS);
    }
}
