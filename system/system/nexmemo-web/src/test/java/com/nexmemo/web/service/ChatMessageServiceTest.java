package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMessageDto;
import com.nexmemo.core.dto.EditMessageRequest;
import com.nexmemo.core.dto.ForwardMessageRequest;
import com.nexmemo.core.dto.MessageStatusDto;
import com.nexmemo.core.dto.SendMessageRequest;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageEditHistory;
import com.nexmemo.core.entity.ChatParticipant;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.core.enums.ChatMessageType;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatMessageEditHistoryRepository;
import com.nexmemo.security.repository.ChatMessageMentionRepository;
import com.nexmemo.security.repository.ChatMessageReactionRepository;
import com.nexmemo.security.repository.ChatMessageReceiptRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.ChatAttachmentRepository;
import com.nexmemo.security.repository.ChatBookmarkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
 * Unit tests for the conversation messaging path: idempotent send, access enforcement,
 * time-boxed author edits, author/admin soft-delete and delivery status.
 */
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatParticipantRepository participantRepository;
    @Mock private ChatMessageReceiptRepository receiptRepository;
    @Mock private ChatMessageEditHistoryRepository editHistoryRepository;
    @Mock private ChatMessageReactionRepository reactionRepository;
    @Mock private ChatMessageMentionRepository mentionRepository;
    @Mock private ChatAttachmentRepository attachmentRepository;
    @Mock private ChatBookmarkRepository bookmarkRepository;
    @Mock private com.nexmemo.security.repository.UserRepository userRepository;
    @Mock private ChatMentionService chatMentionService;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private com.nexmemo.web.service.ChatDocumentService documentService;
    @Mock private com.nexmemo.web.service.ChatDraftService draftService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatMessageService service;

    private static final Long USER = 10L;
    private static final Long PEER = 20L;

    private ChatConversation directConversation() {
        ChatConversation c = new ChatConversation();
        c.setId(1L);
        c.setConversationType(ChatConversationType.DIRECT);
        c.setIsLocked(false);
        return c;
    }

    private SendMessageRequest request(String clientId, String content) {
        SendMessageRequest req = new SendMessageRequest();
        req.setClientMessageId(clientId);
        req.setMessage(content);
        return req;
    }

    private ChatMessage message(Long id, Long sender, String content, LocalDateTime created) {
        ChatMessage m = ChatMessage.builder()
                .id(id)
                .conversationId(1L)
                .conversationType(ChatConversationType.DIRECT.name())
                .senderId(sender)
                .message(content)
                .isDeleted(false)
                .isEdited(false)
                .createdDate(created)
                .build();
        return m;
    }

    // ── send ────────────────────────────────────────────────────────────────────

    @Test
    void send_isIdempotent_returnsExistingMessage() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(5L, USER, "hello", LocalDateTime.now());
        when(messageRepository.findBySenderIdAndClientMessageId(USER, "abc")).thenReturn(Optional.of(existing));
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of());

        ChatMessageDto result = service.send(USER, 1L, request("abc", "hello"));

        assertThat(result.getId()).isEqualTo(5L);
        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void send_persistsMessage_updatesConversation_andBroadcasts() {
        ChatConversation conversation = directConversation();
        when(conversationService.requireConversation(1L)).thenReturn(conversation);
        when(messageRepository.findBySenderIdAndClientMessageId(USER, "abc")).thenReturn(Optional.empty());
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(99L);
            }
            return m;
        });

        ChatParticipant me = new ChatParticipant();
        me.setUserId(USER);
        ChatParticipant peer = new ChatParticipant();
        peer.setUserId(PEER);
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of(me, peer));

        ChatMessageDto result = service.send(USER, 1L, request("abc", "  hi there  "));

        assertThat(result.getId()).isEqualTo(99L);
        assertThat(result.getMessage()).isEqualTo("hi there");
        assertThat(result.getReceiverId()).isEqualTo(PEER);
        assertThat(conversation.getLastMessageId()).isEqualTo(99L);
        assertThat(conversation.getLastMessageAt()).isNotNull();
        verify(conversationRepository).save(conversation);
        verify(conversationService).markRead(USER, 1L, 99L);
        verify(messagingTemplate).convertAndSend("/topic/conversation/1", result);
        verify(messagingTemplate).convertAndSendToUser(PEER.toString(), "/queue/messages", result);
    }

    @Test
    void send_rejectsLockedConversation() {
        ChatConversation conversation = directConversation();
        conversation.setIsLocked(true);
        when(conversationService.requireConversation(1L)).thenReturn(conversation);

        assertThatThrownBy(() -> service.send(USER, 1L, request("abc", "hi")))
                .isInstanceOf(AccessDeniedException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void send_authorizesAccess() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        doThrow(new AccessDeniedException("nope"))
                .when(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));

        assertThatThrownBy(() -> service.send(USER, 1L, request("abc", "hi")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── history ─────────────────────────────────────────────────────────────────

    @Test
    void getHistory_firstPage_callsDescendingQuery() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        when(messageRepository.findByConversationIdAndIsDeletedFalseOrderByIdDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(Page.empty());

        Page<ChatMessageDto> page = service.getHistory(USER, 1L, null, 50);

        assertThat(page).isEmpty();
        verify(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));
        verify(messageRepository, never())
                .findByConversationIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(any(), any(), any());
    }

    @Test
    void getHistory_withBeforeId_callsCursorQuery() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        when(messageRepository.findByConversationIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(
                eq(1L), eq(50L), any(PageRequest.class))).thenReturn(Page.empty());

        service.getHistory(USER, 1L, 50L, 50);

        verify(messageRepository).findByConversationIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(
                eq(1L), eq(50L), any(PageRequest.class));
    }

    // ── edit ────────────────────────────────────────────────────────────────────

    @Test
    void editMessage_authorWithinWindow_appendsHistoryAndBroadcasts() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, USER, "old body", LocalDateTime.now().minusMinutes(5));
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of());

        EditMessageRequest req = new EditMessageRequest();
        req.setMessage("new body");

        ChatMessageDto result = service.editMessage(USER, 1L, 50L, req);

        assertThat(result.getMessage()).isEqualTo("new body");
        assertThat(result.getIsEdited()).isTrue();
        assertThat(result.getEditedAt()).isNotNull();

        ArgumentCaptor<ChatMessageEditHistory> captor = ArgumentCaptor.forClass(ChatMessageEditHistory.class);
        verify(editHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousContent()).isEqualTo("old body");
        assertThat(captor.getValue().getEditedById()).isEqualTo(USER);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1"), eq(result));
    }

    @Test
    void editMessage_deniedAfterWindow() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, USER, "old", LocalDateTime.now().minusMinutes(30));
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));

        EditMessageRequest req = new EditMessageRequest();
        req.setMessage("new");

        assertThatThrownBy(() -> service.editMessage(USER, 1L, 50L, req))
                .isInstanceOf(AccessDeniedException.class);
        verify(editHistoryRepository, never()).save(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void editMessage_deniedForNonAuthor() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, PEER, "peer body", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));

        EditMessageRequest req = new EditMessageRequest();
        req.setMessage("hijack");

        assertThatThrownBy(() -> service.editMessage(USER, 1L, 50L, req))
                .isInstanceOf(AccessDeniedException.class);
        verify(editHistoryRepository, never()).save(any());
    }

    // ── delete ──────────────────────────────────────────────────────────────────

    @Test
    void deleteMessage_authorSoftDeletes_andHidesContentFromOthers() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, USER, "secret body", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of());

        ChatMessageDto result = service.deleteMessage(USER, 1L, 50L, null);

        assertThat(result.getIsDeleted()).isTrue();
        assertThat(result.getMessage()).isNull();
        assertThat(existing.getDeletedById()).isEqualTo(USER);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1"), eq(result));
    }

    @Test
    void deleteMessage_adminMayDeleteOthersMessage_andRetainsContent() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, PEER, "evidence", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatAuth.isAdmin(USER)).thenReturn(true);
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of());

        ChatMessageDto result = service.deleteMessage(USER, 1L, 50L, "policy violation");

        assertThat(result.getIsDeleted()).isTrue();
        assertThat(result.getMessage()).isEqualTo("evidence"); // admin still sees the body
        assertThat(result.getDeletedReason()).isEqualTo("policy violation");
    }

    @Test
    void deleteMessage_deniedForNonAuthorNonAdmin() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, PEER, "peer body", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deleteMessage(USER, 1L, 50L, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(messageRepository, never()).save(any());
    }

    // ── status ──────────────────────────────────────────────────────────────────

    @Test
    void getMessageStatus_readableBySender_reportsReadCount() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, USER, "hi", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(receiptRepository.findByMessageId(50L)).thenReturn(List.of());

        ChatParticipant me = new ChatParticipant();
        me.setUserId(USER);
        ChatParticipant peer = new ChatParticipant();
        peer.setUserId(PEER);
        peer.setLastDeliveredMessageId(60L);
        peer.setLastReadMessageId(60L);
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of(me, peer));

        MessageStatusDto status = service.getMessageStatus(USER, 50L);

        assertThat(status.getDeliveryState()).isEqualTo("READ");
        assertThat(status.getReadCount()).isEqualTo(1);
        assertThat(status.getTotalRecipients()).isEqualTo(1);
    }

    @Test
    void getMessageStatus_deniedForNonSender() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, PEER, "hi", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.getMessageStatus(USER, 50L))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Phase 2c: threads ─────────────────────────────────────────────────────

    @Test
    void getReplies_returnsRenderedReplies_oldestFirst() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage parent = message(50L, USER, "root", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(parent));
        ChatMessage reply = message(51L, PEER, "a reply", LocalDateTime.now());
        reply.setParentMessageId(50L);
        when(messageRepository.findByParentMessageIdAndIsDeletedFalseOrderByIdAsc(
                50L, PageRequest.of(0, 50))).thenReturn(List.of(reply));

        List<ChatMessageDto> replies = service.getReplies(USER, 1L, 50L, null, 50);

        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).getId()).isEqualTo(51L);
        verify(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));
    }

    @Test
    void getReplies_afterId_filtersNewerReplies() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage parent = message(50L, USER, "root", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(parent));
        when(messageRepository.findByParentMessageIdAndIsDeletedFalseAndIdGreaterThanOrderByIdAsc(
                50L, 55L, PageRequest.of(0, 50))).thenReturn(List.of());

        assertThat(service.getReplies(USER, 1L, 50L, 55L, 50)).isEmpty();
    }

    // ── Phase 2d: pin ─────────────────────────────────────────────────────────

    @Test
    void pinMessage_authorTogglesPinned_andBroadcasts() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, USER, "pin me", LocalDateTime.now());
        existing.setIsPinned(false);
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findByConversationIdAndIsActiveTrue(1L)).thenReturn(List.of());

        ChatMessageDto dto = service.pinMessage(USER, 1L, 50L);

        assertThat(dto.getIsPinned()).isTrue();
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1"), eq(dto));
    }

    @Test
    void pinMessage_deniedForNonAuthorNonAdmin() {
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatMessage existing = message(50L, PEER, "peer msg", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.pinMessage(USER, 1L, 50L))
                .isInstanceOf(AccessDeniedException.class);
        verify(messageRepository, never()).save(any());
    }

    // ── Phase 2e: forward ─────────────────────────────────────────────────────

    @Test
    void forwardMessage_copiesIntoTarget_withBothSidesAuthorized() {
        ChatConversation target = new ChatConversation();
        target.setId(2L);
        target.setConversationType(ChatConversationType.BRANCH);
        target.setBranchId(7L);
        target.setIsLocked(false);
        ChatMessage sourceMsg = message(50L, PEER, "forward me", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(sourceMsg));
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        when(conversationService.requireConversation(2L)).thenReturn(target);
        when(messageRepository.findBySenderIdAndClientMessageId(USER, "fwd-1")).thenReturn(Optional.empty());
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            if (m.getId() == null) {
                m.setId(66L);
            }
            return m;
        });
        when(userRepository.findById(PEER)).thenReturn(Optional.of(user(PEER, "Bob")));
        when(participantRepository.findByConversationIdAndIsActiveTrue(2L)).thenReturn(List.of());

        ForwardMessageRequest req = new ForwardMessageRequest();
        req.setTargetConversationId(2L);
        req.setClientMessageId("fwd-1");

        ChatMessageDto dto = service.forwardMessage(USER, 50L, req);

        assertThat(dto.getMessage()).contains("Forwarded from Bob");
        assertThat(dto.getMessageType()).isEqualTo("FORWARD");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getConversationId()).isEqualTo(2L);
        assertThat(captor.getValue().getMessageType()).isEqualTo(ChatMessageType.FORWARD);
        verify(conversationRepository).save(target);
        verify(conversationService).markRead(USER, 2L, 66L);
    }

    @Test
    void forwardMessage_deniedWithoutTargetAccess() {
        ChatMessage sourceMsg = message(50L, PEER, "forward me", LocalDateTime.now());
        when(messageRepository.findById(50L)).thenReturn(Optional.of(sourceMsg));
        when(conversationService.requireConversation(1L)).thenReturn(directConversation());
        ChatConversation target = new ChatConversation();
        target.setId(2L);
        target.setConversationType(ChatConversationType.BRANCH);
        when(conversationService.requireConversation(2L)).thenReturn(target);
        // Source read passes; only the target write is denied (match by conversation id).
        doAnswer(inv -> {
            ChatConversation c = inv.getArgument(1);
            if (c != null && c.getId() != null && c.getId() == 2L) {
                throw new AccessDeniedException("nope");
            }
            return null;
        }).when(chatAuth).assertCanAccessConversation(anyLong(), any());

        ForwardMessageRequest req = new ForwardMessageRequest();
        req.setTargetConversationId(2L);

        assertThatThrownBy(() -> service.forwardMessage(USER, 50L, req))
                .isInstanceOf(AccessDeniedException.class);
        verify(messageRepository, never()).save(any());
    }

    private com.nexmemo.core.entity.User user(Long id, String name) {
        com.nexmemo.core.entity.User u = new com.nexmemo.core.entity.User();
        u.setId(id);
        u.setName(name);
        return u;
    }
}
