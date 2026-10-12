package com.nexmemo.web.service;

import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the typing indicator: authorization and per-user rate limiting
 * (at most one event per user per conversation within the cooldown window).
 */
@ExtendWith(MockitoExtension.class)
class ChatTypingServiceTest {

    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private ChatTypingService service;

    private static final Long USER = 10L;
    private static final Long CONVERSATION = 1L;

    private ChatConversation conversation() {
        ChatConversation c = new ChatConversation();
        c.setId(CONVERSATION);
        c.setConversationType(ChatConversationType.GROUP);
        return c;
    }

    @Test
    void notifyTyping_broadcastsTypingEvent() {
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation());
        User user = new User();
        user.setId(USER);
        user.setName("Alice");
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));

        service.notifyTyping(USER, CONVERSATION);

        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/1/typing"), any(Object.class));
    }

    @Test
    void notifyTyping_rateLimitsWithinCooldown() {
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation());
        User user = new User();
        user.setId(USER);
        user.setName("Alice");
        when(userRepository.findById(USER)).thenReturn(Optional.of(user));

        service.notifyTyping(USER, CONVERSATION);
        service.notifyTyping(USER, CONVERSATION); // falls inside the 1.5 s window → dropped

        verify(messagingTemplate, times(1)).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void notifyTyping_authorizesConversationAccess() {
        when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation());
        doThrow(new org.springframework.security.access.AccessDeniedException("nope"))
                .when(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));

        assertThatThrownBy(() -> service.notifyTyping(USER, CONVERSATION))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }
}