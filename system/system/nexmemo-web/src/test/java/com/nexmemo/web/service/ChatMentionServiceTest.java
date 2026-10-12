package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMentionDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageMention;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.security.repository.ChatMessageMentionRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for @mentions: token parsing, authorization filtering, persistence/notification
 * on send/edit, deletion cleanup and the caller's tagged inbox.
 */
@ExtendWith(MockitoExtension.class)
class ChatMentionServiceTest {

    @Mock private ChatMessageMentionRepository mentionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private NotificationService notificationService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatMentionService service;

    private static final Long SENDER = 10L;
    private static final Long PEER = 20L;
    private static final Long OTHER = 30L;
    private static final Long MESSAGE_ID = 50L;

    private ChatConversation conversation() {
        ChatConversation c = new ChatConversation();
        c.setId(1L);
        c.setConversationType(ChatConversationType.DIRECT);
        return c;
    }

    private ChatMessage message(String content) {
        return ChatMessage.builder()
                .id(MESSAGE_ID)
                .conversationId(1L)
                .conversationType(ChatConversationType.DIRECT.name())
                .senderId(SENDER)
                .message(content)
                .isDeleted(false)
                .build();
    }

    private ChatMessageMention row(Long userId) {
        return ChatMessageMention.builder()
                .messageId(MESSAGE_ID)
                .conversationId(1L)
                .mentionedUserId(userId)
                .build();
    }

    // ── parsing ──────────────────────────────────────────────────────────────

    @Test
    void parsesTokens_deduplicates_andIgnoresMalformed() {
        String content = "Hey @[Alice](20) and @[Bob](30), see @[Alice](20) again. @fake 42";
        List<Long> ids = ChatMentionService.parseMentionedIds(content);

        assertThat(ids).containsExactly(20L, 30L);
    }

    @Test
    void preview_resolvesTokensAndTruncates() {
        String content = "@[Alice One](20) please  review  the  memo " + "x".repeat(200);
        String preview = ChatMentionService.preview(content);

        assertThat(preview).startsWith("@Alice One please review the memo");
        assertThat(preview).endsWith("...");
        assertThat(preview).hasSizeLessThanOrEqualTo(123);
    }

    @Test
    void parseHandlesNullAndEmpty() {
        assertThat(ChatMentionService.parseMentionedIds(null)).isEmpty();
        assertThat(ChatMentionService.parseMentionedIds("no mentions here")).isEmpty();
    }

    // ── sync on send / edit ──────────────────────────────────────────────────

    @Test
    void syncMentions_persistsAuthorizedTargets_andNotifiesThem() {
        ChatMessage message = message("Hi @[Alice](20) @[Carol](30)");
        when(mentionRepository.findByMessageId(MESSAGE_ID)).thenReturn(List.of());
        when(userRepository.existsById(20L)).thenReturn(true);
        when(userRepository.existsById(30L)).thenReturn(true);
        when(chatAuth.canAccessConversation(eq(20L), any(ChatConversation.class))).thenReturn(true);
        when(chatAuth.canAccessConversation(eq(30L), any(ChatConversation.class))).thenReturn(true);
        when(userRepository.findById(SENDER)).thenReturn(java.util.Optional.of(user(SENDER, "Sender")));

        service.syncMentions(conversation(), message, SENDER);

        ArgumentCaptor<ChatMessageMention> captor = ArgumentCaptor.forClass(ChatMessageMention.class);
        verify(mentionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ChatMessageMention::getMentionedUserId)
                .containsExactlyInAnyOrder(20L, 30L);

        verify(notificationService).createNotification(eq(20L), eq("You were mentioned"),
                any(String.class), eq("CHAT_MENTION"), eq(1L), eq("CONVERSATION"));
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/30"),
                any(com.nexmemo.core.dto.ChatMentionEventDto.class));
    }

    @Test
    void syncMentions_dropsSelfUnauthorizedAndUnknownUsers() {
        ChatMessage message = message("hi @[Me](10) @[Nope](99) @[Hard](30)");
        when(mentionRepository.findByMessageId(MESSAGE_ID)).thenReturn(List.of());
        // 99 does not exist; 30 exists but has no access; 10 is the sender.
        when(userRepository.existsById(99L)).thenReturn(false);
        when(userRepository.existsById(30L)).thenReturn(true);
        when(chatAuth.canAccessConversation(eq(30L), any(ChatConversation.class))).thenReturn(false);

        service.syncMentions(conversation(), message, SENDER);

        verify(mentionRepository, never()).save(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void syncMentions_onEdit_removesStale_andNotifiesOnlyNew() {
        ChatMessage message = message("@[Alice](20) changed");
        when(mentionRepository.findByMessageId(MESSAGE_ID)).thenReturn(List.of(row(30L))); // stale tag
        when(userRepository.existsById(20L)).thenReturn(true);
        when(chatAuth.canAccessConversation(eq(20L), any(ChatConversation.class))).thenReturn(true);
        when(userRepository.findById(SENDER)).thenReturn(java.util.Optional.of(user(SENDER, "Sender")));

        service.syncMentions(conversation(), message, SENDER);

        verify(mentionRepository).delete(argThat(m -> m.getMentionedUserId().equals(30L)));
        ArgumentCaptor<ChatMessageMention> captor = ArgumentCaptor.forClass(ChatMessageMention.class);
        verify(mentionRepository).save(captor.capture());
        assertThat(captor.getValue().getMentionedUserId()).isEqualTo(20L);
        // Only the newly added user is notified; 30 was already tagged before.
        verify(notificationService).createNotification(eq(20L), eq("You were mentioned"),
                any(String.class), eq("CHAT_MENTION"), eq(1L), eq("CONVERSATION"));
        verify(notificationService, never())
                .createNotification(eq(30L), any(), any(), any(), any(), any());
    }

    @Test
    void removeForMessage_deletesRows() {
        service.removeForMessage(MESSAGE_ID);
        verify(mentionRepository).deleteByMessageId(MESSAGE_ID);
    }

    // ── tagged inbox ─────────────────────────────────────────────────────────

    @Test
    void getMyMentions_mapsNamesAndSkipsDeletedMessages() {
        ChatMessageMention good = row(PEER);
        good.setCreatedDate(java.time.LocalDateTime.now());
        ChatMessageMention deleted = row(PEER);
        deleted.setMessageId(51L);

        when(mentionRepository.findByMentionedUserIdOrderByIdDesc(PEER, PageRequest.of(0, 30)))
                .thenReturn(List.of(deleted, good));

        ChatMessage gm = ChatMessage.builder()
                .id(MESSAGE_ID).conversationId(1L).senderId(SENDER)
                .message("@[Alice](20) please check").isDeleted(false).build();
        ChatMessage dm = ChatMessage.builder()
                .id(51L).conversationId(1L).senderId(SENDER).message("gone").isDeleted(true).build();
        when(messageRepository.findAllById(any())).thenReturn(List.of(gm, dm));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(SENDER, "Sender Person")));

        List<ChatMentionDto> result = service.getMyMentions(PEER, 30);

        assertThat(result).hasSize(1);
        ChatMentionDto dto = result.get(0);
        assertThat(dto.getMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(dto.getMentionedByName()).isEqualTo("Sender Person");
        assertThat(dto.getPreview()).isEqualTo("@Alice please check");
        assertThat(dto.getCreatedDate()).isNotNull();
    }

    private User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }
}