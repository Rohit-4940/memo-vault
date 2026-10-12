package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatConversationSummaryDto;
import com.nexmemo.core.dto.CreateConversationRequest;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatParticipant;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.core.enums.ChatNotificationLevel;
import com.nexmemo.core.enums.ChatParticipantRole;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatMessageReceiptRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Phase 1a conversation lifecycle (mocked persistence).
 */
@ExtendWith(MockitoExtension.class)
class ChatConversationServiceTest {

    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatParticipantRepository participantRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatMessageReceiptRepository receiptRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatConversationService service;

    private static final Long USER = 10L;
    private static final Long PEER = 20L;

    private void stubNewConversationId(long id) {
        when(conversationRepository.save(any(ChatConversation.class))).thenAnswer(inv -> {
            ChatConversation c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(id);
            }
            return c;
        });
    }

    @Test
    void createOrGetDirect_createsConversation_andAddsBothParticipants() {
        when(conversationRepository.findByDedupeKey("DIRECT:10:20")).thenReturn(Optional.empty());
        stubNewConversationId(100L);
        when(participantRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());

        CreateConversationRequest req = new CreateConversationRequest();
        req.setConversationType("DIRECT");
        req.setOtherUserId(PEER);

        ChatConversation result = service.createOrGet(USER, req);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getConversationType()).isEqualTo(ChatConversationType.DIRECT);
        verify(chatAuth).assertCanDirectMessage(USER, PEER);

        ArgumentCaptor<ChatParticipant> captor = ArgumentCaptor.forClass(ChatParticipant.class);
        verify(participantRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ChatParticipant::getUserId)
                .containsExactlyInAnyOrder(USER, PEER);
    }

    @Test
    void createOrGetDirect_reusesExistingConversation() {
        ChatConversation existing = new ChatConversation();
        existing.setId(55L);
        existing.setConversationType(ChatConversationType.DIRECT);
        when(conversationRepository.findByDedupeKey("DIRECT:10:20")).thenReturn(Optional.of(existing));
        when(participantRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());

        CreateConversationRequest req = new CreateConversationRequest();
        req.setConversationType("DIRECT");
        req.setOtherUserId(PEER);

        ChatConversation result = service.createOrGet(USER, req);

        assertThat(result.getId()).isEqualTo(55L);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createOrGetMemo_assertsMemoAccess() {
        when(conversationRepository.findByDedupeKey("MEMO:7")).thenReturn(Optional.empty());
        stubNewConversationId(77L);
        when(participantRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());

        CreateConversationRequest req = new CreateConversationRequest();
        req.setConversationType("MEMO");
        req.setMemoId(7L);

        ChatConversation result = service.createOrGet(USER, req);

        assertThat(result.getConversationType()).isEqualTo(ChatConversationType.MEMO);
        assertThat(result.getMemoId()).isEqualTo(7L);
        verify(chatAuth).assertCanAccessMemo(USER, 7L);
    }

    @Test
    void createGroup_deniedForNonAdmin() {
        doThrow(new AccessDeniedException("group creation requires admin"))
                .when(chatAuth).assertCanCreateGroup(USER);

        CreateConversationRequest req = new CreateConversationRequest();
        req.setConversationType("GROUP");
        req.setTitle("Team");

        assertThatThrownBy(() -> service.createOrGet(USER, req))
                .isInstanceOf(AccessDeniedException.class);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createGroup_addsCreatorAsOwner_andMembers() {
        stubNewConversationId(200L);
        User active = new User();
        active.setId(PEER);
        active.setStatus(Status.ACTIVE);
        when(userRepository.findById(PEER)).thenReturn(Optional.of(active));
        when(participantRepository.findByConversationIdAndUserId(any(), any())).thenReturn(Optional.empty());

        CreateConversationRequest req = new CreateConversationRequest();
        req.setConversationType("GROUP");
        req.setTitle("Team");
        req.setMemberIds(List.of(PEER));

        service.createOrGet(USER, req);

        ArgumentCaptor<ChatParticipant> captor = ArgumentCaptor.forClass(ChatParticipant.class);
        verify(participantRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(ChatParticipant::getParticipantRole)
                .containsExactlyInAnyOrder(ChatParticipantRole.OWNER, ChatParticipantRole.MEMBER);
    }

    @Test
    void removeParticipant_selfLeave_allowedWithoutManageCheck() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        ChatParticipant participant = new ChatParticipant();
        participant.setIsActive(true);
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.removeParticipant(USER, 1L, USER);

        assertThat(participant.getIsActive()).isFalse();
        assertThat(participant.getLeftAt()).isNotNull();
        verify(chatAuth, never()).assertCanManageConversation(any(), any());
    }

    @Test
    void listMyConversations_reportsUnreadAndPreview() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.DIRECT);
        conversation.setLastMessageAt(LocalDateTime.now());

        ChatParticipant me = new ChatParticipant();
        me.setLastReadMessageId(10L);

        ChatMessage last = ChatMessage.builder().id(15L).message("hello world").build();

        when(participantRepository.findActiveConversationIdsByUserId(USER)).thenReturn(List.of(1L));
        when(conversationRepository.findAllById(any())).thenReturn(List.of(conversation));
        when(messageRepository.findTopByConversationIdOrderByIdDesc(1L)).thenReturn(Optional.of(last));
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(me));
        when(messageRepository.countByConversationIdAndIdGreaterThanAndSenderIdNot(1L, 10L, USER)).thenReturn(3L);

        List<ChatConversationSummaryDto> summaries = service.listMyConversations(USER);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getUnreadCount()).isEqualTo(3L);
        assertThat(summaries.get(0).getLastMessagePreview()).isEqualTo("hello world");
    }

    @Test
    void markRead_advancesLastReadMessageId_monotonically() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        ChatParticipant participant = new ChatParticipant();
        participant.setLastReadMessageId(50L);
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.markRead(USER, 1L, 40L); // older id must not regress
        assertThat(participant.getLastReadMessageId()).isEqualTo(50L);

        service.markRead(USER, 1L, 60L);
        assertThat(participant.getLastReadMessageId()).isEqualTo(60L);
        verify(participantRepository, times(2)).save(eq(participant));
    }

    @Test
    void markRead_advancesDeliveredWatermark_too() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.DIRECT);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        ChatParticipant participant = new ChatParticipant();
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.markRead(USER, 1L, 42L);

        assertThat(participant.getLastReadMessageId()).isEqualTo(42L);
        assertThat(participant.getLastDeliveredMessageId()).isEqualTo(42L);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1/receipts"), any(Object.class));
    }

    @Test
    void markDelivered_setsWatermarkToTail_andBroadcasts() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.BRANCH);
        conversation.setLastMessageId(90L);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        ChatParticipant participant = new ChatParticipant();
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.markDelivered(USER, 1L);

        assertThat(participant.getLastDeliveredMessageId()).isEqualTo(90L);
        verify(participantRepository).save(participant);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1/receipts"), any(Object.class));
    }

    @Test
    void markDelivered_isNoOp_whenAlreadyDelivered() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.BRANCH);
        conversation.setLastMessageId(90L);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        ChatParticipant participant = new ChatParticipant();
        participant.setLastDeliveredMessageId(90L);
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.markDelivered(USER, 1L);

        verify(participantRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    // ── Phase 2d: mark-unread ─────────────────────────────────────────────────

    @Test
    void markUnread_rewindsReadWatermark_belowMessageId() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        ChatParticipant participant = new ChatParticipant();
        participant.setLastReadMessageId(60L);
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.markUnread(USER, 1L, 40L);

        assertThat(participant.getLastReadMessageId()).isEqualTo(39L);
        assertThat(participant.getLastReadAt()).isNull();
    }

    @Test
    void markUnread_neverMovesWatermarkForward() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        ChatParticipant participant = new ChatParticipant();
        participant.setLastReadMessageId(10L);
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        service.markUnread(USER, 1L, 100L);

        assertThat(participant.getLastReadMessageId()).isEqualTo(10L);
        verify(participantRepository, never()).save(any());
    }

    // ── Phase 2h: notification settings ───────────────────────────────────────

    @Test
    void updateNotificationSetting_savesLevel_onOwnParticipant() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.BRANCH);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        ChatParticipant participant = new ChatParticipant();
        when(participantRepository.findByConversationIdAndUserId(1L, USER)).thenReturn(Optional.of(participant));

        String level = service.updateNotificationSetting(USER, 1L, "MENTIONS");

        assertThat(level).isEqualTo("MENTIONS");
        assertThat(participant.getNotificationLevel()).isEqualTo(ChatNotificationLevel.MENTIONS);
        verify(participantRepository).save(participant);
    }

    @Test
    void updateNotificationSetting_rejectsUnknownLevel() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.BRANCH);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        assertThatThrownBy(() -> service.updateNotificationSetting(USER, 1L, "LOUD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Phase 2j: lock + members ──────────────────────────────────────────────

    @Test
    void setLocked_requiresManagePermission_andBroadcastsMeta() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(conversation)).thenReturn(conversation);

        ChatConversation result = service.setLocked(USER, 1L, true);

        assertThat(result.getIsLocked()).isTrue();
        verify(chatAuth).assertCanManageConversation(USER, conversation);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1/meta"), any(Object.class));
    }

    @Test
    void setLocked_deniedForNonManager() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(1L);
        conversation.setConversationType(ChatConversationType.BRANCH);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        doThrow(new AccessDeniedException("nope"))
                .when(chatAuth).assertCanManageConversation(eq(USER), any(ChatConversation.class));

        assertThatThrownBy(() -> service.setLocked(USER, 1L, true))
                .isInstanceOf(AccessDeniedException.class);
        verify(conversationRepository, never()).save(any());
    }
}
