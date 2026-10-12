package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatReactionEventDto;
import com.nexmemo.core.dto.ChatReactionSummaryDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageReaction;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.security.repository.ChatMessageReactionRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for emoji reactions: idempotency, authorization, soft-delete rejection,
 * validation and realtime broadcast.
 */
@ExtendWith(MockitoExtension.class)
class ChatReactionServiceTest {

    @Mock private ChatMessageReactionRepository reactionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatReactionService service;

    private static final Long USER = 10L;
    private static final Long PEER = 20L;
    private static final Long MESSAGE_ID = 50L;

    private ChatConversation conversation() {
        ChatConversation c = new ChatConversation();
        c.setId(1L);
        c.setConversationType(ChatConversationType.DIRECT);
        return c;
    }

    private ChatMessage message(boolean deleted) {
        return ChatMessage.builder()
                .id(MESSAGE_ID)
                .conversationId(1L)
                .conversationType(ChatConversationType.DIRECT.name())
                .senderId(PEER)
                .message("hello")
                .isDeleted(deleted)
                .build();
    }

    private ChatMessageReaction row(Long userId, String emoji) {
        return ChatMessageReaction.builder()
                .messageId(MESSAGE_ID)
                .userId(userId)
                .emoji(emoji)
                .build();
    }

    private void accessibleMessage() {
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message(false)));
        when(conversationService.requireConversation(1L)).thenReturn(conversation());
    }

    @Test
    void addReaction_createsRow_andBroadcastsAdded() {
        accessibleMessage();
        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(MESSAGE_ID, USER, "👍"))
                .thenReturn(Optional.empty());
        when(reactionRepository.countByMessageIdAndEmoji(MESSAGE_ID, "👍")).thenReturn(1L);
        when(reactionRepository.findByMessageIdOrderByCreatedDateAsc(MESSAGE_ID))
                .thenReturn(List.of(row(USER, "👍")));

        List<ChatReactionSummaryDto> result = service.addReaction(USER, MESSAGE_ID, "👍");

        verify(reactionRepository).save(any(ChatMessageReaction.class));
        ArgumentCaptor<ChatReactionEventDto> captor = ArgumentCaptor.forClass(ChatReactionEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1/reactions"), captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("ADDED");
        assertThat(captor.getValue().getCount()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmoji()).isEqualTo("👍");
        assertThat(result.get(0).getCount()).isEqualTo(1);
        assertThat(result.get(0).isReactedByMe()).isTrue();
    }

    @Test
    void addReaction_isIdempotent_doesNotDuplicateNorBroadcast() {
        accessibleMessage();
        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(MESSAGE_ID, USER, "👍"))
                .thenReturn(Optional.of(row(USER, "👍")));
        when(reactionRepository.findByMessageIdOrderByCreatedDateAsc(MESSAGE_ID))
                .thenReturn(List.of(row(USER, "👍")));

        service.addReaction(USER, MESSAGE_ID, "👍");

        verify(reactionRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void removeReaction_deletesRow_andBroadcastsRemoved() {
        accessibleMessage();
        ChatMessageReaction existing = row(USER, "❤️");
        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(MESSAGE_ID, USER, "❤️"))
                .thenReturn(Optional.of(existing));
        when(reactionRepository.countByMessageIdAndEmoji(MESSAGE_ID, "❤️")).thenReturn(0L);
        when(reactionRepository.findByMessageIdOrderByCreatedDateAsc(MESSAGE_ID)).thenReturn(List.of());

        service.removeReaction(USER, MESSAGE_ID, "❤️");

        verify(reactionRepository).delete(existing);
        ArgumentCaptor<ChatReactionEventDto> captor = ArgumentCaptor.forClass(ChatReactionEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1/reactions"), captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("REMOVED");
        assertThat(captor.getValue().getCount()).isZero();
    }

    @Test
    void removeReaction_isIdempotent_whenAbsent() {
        accessibleMessage();
        when(reactionRepository.findByMessageIdAndUserIdAndEmoji(MESSAGE_ID, USER, "❤️"))
                .thenReturn(Optional.empty());
        when(reactionRepository.findByMessageIdOrderByCreatedDateAsc(MESSAGE_ID)).thenReturn(List.of());

        service.removeReaction(USER, MESSAGE_ID, "❤️");

        verify(reactionRepository, never()).delete(any());
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void addReaction_rejectsDeletedMessage() {
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message(true)));
        when(conversationService.requireConversation(1L)).thenReturn(conversation());

        assertThatThrownBy(() -> service.addReaction(USER, MESSAGE_ID, "👍"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deleted");
        verify(reactionRepository, never()).save(any());
    }

    @Test
    void addReaction_rejectsMessageWithoutConversation() {
        ChatMessage legacy = ChatMessage.builder().id(MESSAGE_ID).message("legacy").build();
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.addReaction(USER, MESSAGE_ID, "👍"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reactionRepository, never()).save(any());
    }

    @Test
    void addReaction_enforcesConversationAccess() {
        when(messageRepository.findById(MESSAGE_ID)).thenReturn(Optional.of(message(false)));
        when(conversationService.requireConversation(1L)).thenReturn(conversation());
        doThrow(new AccessDeniedException("nope"))
                .when(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));

        assertThatThrownBy(() -> service.addReaction(USER, MESSAGE_ID, "👍"))
                .isInstanceOf(AccessDeniedException.class);
        verify(reactionRepository, never()).save(any());
    }

    @Test
    void addReaction_rejectsBlankEmoji() {
        assertThatThrownBy(() -> service.addReaction(USER, MESSAGE_ID, "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        verifyNoInteractions(messageRepository);
    }

    @Test
    void addReaction_rejectsOverlongEmoji() {
        String tooLong = "x".repeat(33);
        assertThatThrownBy(() -> service.addReaction(USER, MESSAGE_ID, tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("long");
        verifyNoInteractions(messageRepository);
    }

    @Test
    void summarize_groupsByEmoji_andFlagsViewer() {
        List<ChatMessageReaction> rows = List.of(
                row(USER, "👍"),
                row(PEER, "👍"),
                row(PEER, "🎉"));

        List<ChatReactionSummaryDto> summaries = ChatReactionService.summarize(rows, USER);

        assertThat(summaries).hasSize(2);
        ChatReactionSummaryDto thumbs = summaries.stream()
                .filter(s -> s.getEmoji().equals("👍")).findFirst().orElseThrow();
        assertThat(thumbs.getCount()).isEqualTo(2);
        assertThat(thumbs.isReactedByMe()).isTrue();
        assertThat(thumbs.getUserIds()).containsExactlyInAnyOrder(USER, PEER);

        ChatReactionSummaryDto party = summaries.stream()
                .filter(s -> s.getEmoji().equals("🎉")).findFirst().orElseThrow();
        assertThat(party.getCount()).isEqualTo(1);
        assertThat(party.isReactedByMe()).isFalse();
    }
}
