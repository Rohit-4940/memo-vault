package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMessageDto;
import com.nexmemo.core.dto.ChatReportDto;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for scoped search (authorization scope from participant/branch/department
 * membership) and the admin-only moderation queue.
 */
@ExtendWith(MockitoExtension.class)
class ChatSearchServiceTest {

    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatConversationRepository conversationRepository;
    @Mock private ChatParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private ChatMessageService messageService;

    @InjectMocks private ChatSearchService service;

    private static final Long USER = 10L;

    @Test
    void search_emptyQuery_returnsEmptyPage() {
        Page<ChatMessageDto> page = service.search(USER, "   ", 0, 50);
        assertThat(page).isEmpty();
        verifyNoInteractions(messageRepository);
    }

    @Test
    void search_withNoAccessibleConversations_returnsEmptyPage() {
        when(participantRepository.findActiveConversationIdsByUserId(USER)).thenReturn(List.of());
        when(userRepository.findBranchIdsByUserId(USER)).thenReturn(List.of());
        when(userRepository.findDepartmentIdsByUserId(USER)).thenReturn(List.of());

        Page<ChatMessageDto> page = service.search(USER, "memo", 0, 50);

        assertThat(page).isEmpty();
        verify(messageRepository, never()).searchInConversations(any(), any(), any());
    }

    @Test
    void search_scopesToParticipantBranchAndDepartmentConversations() {
        when(participantRepository.findActiveConversationIdsByUserId(USER)).thenReturn(List.of(1L));
        when(userRepository.findBranchIdsByUserId(USER)).thenReturn(List.of(11L, 12L));
        when(conversationRepository.findIdsByConversationTypeAndBranchIdIn(List.of(11L, 12L)))
                .thenReturn(List.of(2L));
        when(userRepository.findDepartmentIdsByUserId(USER)).thenReturn(List.of(21L));
        when(conversationRepository.findIdsByConversationTypeAndDepartmentIdIn(List.of(21L)))
                .thenReturn(List.of(3L));
        ChatMessage found = ChatMessage.builder().id(9L).build();
        when(messageRepository.searchInConversations(eq(List.of(1L, 2L, 3L)), eq("memo"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(found)));
        when(messageService.renderMessages(USER, List.of(found))).thenReturn(
                List.of(ChatMessageDto.builder().id(9L).build()));

        Page<ChatMessageDto> page = service.search(USER, "memo", 0, 50);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(9L);
    }

    @Test
    void adminReports_deniedForNonAdmin() {
        when(chatAuth.isAdmin(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.adminReports(USER, 0, 50))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminReports_listsProblemReports_withSenderNames() {
        when(chatAuth.isAdmin(USER)).thenReturn(true);
        ChatMessage report = ChatMessage.builder()
                .id(5L).conversationId(2L).branchId(7L).senderId(USER)
                .message("⚠ Unauthorized sharing in the branch").isDeleted(false).build();
        when(messageRepository.findProblemReports(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(report)));
        com.nexmemo.core.entity.User sender = new com.nexmemo.core.entity.User();
        sender.setId(USER);
        sender.setName("Alice");
        when(userRepository.findById(USER)).thenReturn(java.util.Optional.of(sender));

        Page<ChatReportDto> page = service.adminReports(USER, 0, 50);

        assertThat(page.getContent()).hasSize(1);
        ChatReportDto dto = page.getContent().get(0);
        assertThat(dto.getMessageId()).isEqualTo(5L);
        assertThat(dto.getSenderName()).isEqualTo("Alice");
        assertThat(dto.getPreview()).startsWith("⚠");
    }
}