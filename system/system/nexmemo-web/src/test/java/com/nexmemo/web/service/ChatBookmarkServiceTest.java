package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatBookmarkDto;
import com.nexmemo.core.entity.ChatBookmark;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.security.repository.ChatBookmarkRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for per-user bookmarks: authorized add (idempotent), remove, list mapping
 * that never surfaces deleted content, and the bulk bookmark-id query for history rendering.
 */
@ExtendWith(MockitoExtension.class)
class ChatBookmarkServiceTest {

    @Mock private ChatBookmarkRepository bookmarkRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;

    @InjectMocks private ChatBookmarkService service;

    private static final Long USER = 10L;
    private static final Long CONVERSATION = 1L;
    private static final Long MESSAGE = 50L;

    private ChatMessage message() {
        ChatMessage m = ChatMessage.builder()
                .id(MESSAGE).conversationId(CONVERSATION).senderId(USER)
                .message("important memo content").isDeleted(false).build();
        return m;
    }

    @Test
    void add_savesBookmark_whenNotPresent() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(CONVERSATION);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(messageRepository.findById(MESSAGE)).thenReturn(Optional.of(message()));
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation);
        when(bookmarkRepository.findByUserIdAndMessageId(USER, MESSAGE)).thenReturn(Optional.empty());
        when(userRepository.findById(USER)).thenReturn(Optional.of(user(USER, "Alice")));

        ChatBookmarkDto dto = service.add(USER, MESSAGE);

        assertThat(dto.getMessageId()).isEqualTo(MESSAGE);
        assertThat(dto.getSenderName()).isEqualTo("Alice");
        verify(bookmarkRepository).save(any(ChatBookmark.class));
    }

    @Test
    void add_doesNotDuplicate_existingBookmark() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(CONVERSATION);
        when(messageRepository.findById(MESSAGE)).thenReturn(Optional.of(message()));
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation);
        when(bookmarkRepository.findByUserIdAndMessageId(USER, MESSAGE))
                .thenReturn(Optional.of(ChatBookmark.builder().build()));
        when(userRepository.findById(USER)).thenReturn(Optional.of(user(USER, "Alice")));

        service.add(USER, MESSAGE);

        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void add_requiresConversationAccess() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(CONVERSATION);
        conversation.setConversationType(ChatConversationType.GROUP);
        when(messageRepository.findById(MESSAGE)).thenReturn(Optional.of(message()));
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation);
        doThrow(new org.springframework.security.access.AccessDeniedException("nope"))
                .when(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));

        assertThatThrownBy(() -> service.add(USER, MESSAGE))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(bookmarkRepository, never()).save(any());
    }

    @Test
    void remove_deletesByUserIdAndMessageId() {
        service.remove(USER, MESSAGE);
        verify(bookmarkRepository).deleteByUserIdAndMessageId(USER, MESSAGE);
    }

    @Test
    void list_skipsDeletedMessages_andMapsNames() {
        ChatBookmark good = ChatBookmark.builder()
                .userId(USER).messageId(MESSAGE).conversationId(CONVERSATION).build();
        ChatBookmark deleted = ChatBookmark.builder()
                .userId(USER).messageId(51L).conversationId(CONVERSATION).build();
        when(bookmarkRepository.findByUserIdOrderByIdDesc(eq(USER), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(deleted, good));

        ChatMessage gm = message();
        gm.setMessage("a".repeat(200));
        ChatMessage dm = ChatMessage.builder()
                .id(51L).conversationId(CONVERSATION).senderId(USER).message("gone").isDeleted(true).build();
        when(messageRepository.findAllById(any())).thenReturn(List.of(gm, dm));
        when(userRepository.findAllById(any())).thenReturn(List.of(user(USER, "Alice")));
        ChatConversation conversation = new ChatConversation();
        conversation.setId(CONVERSATION);
        conversation.setTitle("Team Channel");
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation);

        List<ChatBookmarkDto> result = service.list(USER, 50);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageId()).isEqualTo(MESSAGE);
        assertThat(result.get(0).getConversationTitle()).isEqualTo("Team Channel");
        assertThat(result.get(0).getPreview()).endsWith("...");
    }

    @Test
    void bookmarkedMessageIds_returnsBulkSet() {
        when(bookmarkRepository.findByUserIdAndMessageIdIn(USER, List.of(MESSAGE, 51L)))
                .thenReturn(List.of(ChatBookmark.builder().userId(USER).messageId(MESSAGE).build()));

        var ids = service.bookmarkedMessageIds(USER, List.of(MESSAGE, 51L));

        assertThat(ids).containsExactly(MESSAGE);
    }

    private User user(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        return u;
    }
}