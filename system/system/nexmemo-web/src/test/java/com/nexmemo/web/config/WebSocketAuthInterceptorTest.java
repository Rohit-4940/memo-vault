package com.nexmemo.web.config;

import com.nexmemo.security.service.TokenBlacklistService;
import com.nexmemo.security.util.JwtUtil;
import com.nexmemo.web.service.ChatAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for STOMP SUBSCRIBE authorization (Phase 0 hardening).
 * A user must not be able to subscribe to another branch's or another user's stream.
 */
@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private TokenBlacklistService blacklistService;
    @Mock private ChatAuthorizationService chatAuthorizationService;
    @Mock private MessageChannel channel;

    private WebSocketAuthInterceptor interceptor;

    private static final Long USER = 10L;
    private static final Long OTHER = 20L;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(jwtUtil, blacklistService, chatAuthorizationService);
    }

    private Message<byte[]> subscribe(String destination, Long principalId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (principalId != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(principalId, null, List.of()));
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void userDestinationAlwaysAllowed() {
        assertThat(interceptor.preSend(subscribe("/user/queue/messages", USER), channel)).isNotNull();
    }

    @Test
    void unknownDestinationDeniedByDefault() {
        assertThat(interceptor.preSend(subscribe("/topic/secret", USER), channel)).isNull();
    }

    @Test
    void unauthenticatedSubscribeDenied() {
        assertThat(interceptor.preSend(subscribe("/topic/branch/5", null), channel)).isNull();
    }

    @Test
    void branchSubscriptionAllowedForMember_deniedForNonMember() {
        when(chatAuthorizationService.canAccessBranch(USER, 5L)).thenReturn(true);
        when(chatAuthorizationService.canAccessBranch(USER, 6L)).thenReturn(false);

        assertThat(interceptor.preSend(subscribe("/topic/branch/5", USER), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/branch/6", USER), channel)).isNull();
    }

    @Test
    void ownNotificationsAllowed_otherUserNotificationsDenied() {
        assertThat(interceptor.preSend(subscribe("/topic/notifications/" + USER, USER), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/notifications/" + OTHER, USER), channel)).isNull();
    }

    @Test
    void ownMessageQueueAllowed_otherQueueDenied() {
        assertThat(interceptor.preSend(subscribe("/queue/messages/" + USER, USER), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/queue/messages/" + OTHER, USER), channel)).isNull();
    }

    @Test
    void malformedBranchIdDenied() {
        assertThat(interceptor.preSend(subscribe("/topic/branch/not-a-number", USER), channel)).isNull();
    }

    @Test
    void conversationTopicAndSubTopicsAllowedOnlyForMembers() {
        when(chatAuthorizationService.canAccessConversation(USER, 7L)).thenReturn(true);
        when(chatAuthorizationService.canAccessConversation(USER, 8L)).thenReturn(false);

        // The first segment identifies the conversation even for sub-topics (receipts/reactions).
        assertThat(interceptor.preSend(subscribe("/topic/conversation/7", USER), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/conversation/7/receipts", USER), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/conversation/7/reactions", USER), channel)).isNotNull();
        assertThat(interceptor.preSend(subscribe("/topic/conversation/8/reactions", USER), channel)).isNull();
    }
}
