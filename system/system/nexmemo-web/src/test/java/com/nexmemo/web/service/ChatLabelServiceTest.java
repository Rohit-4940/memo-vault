package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatLabelDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatConversationLabel;
import com.nexmemo.security.repository.ChatConversationLabelAssignmentRepository;
import com.nexmemo.security.repository.ChatConversationLabelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatLabelServiceTest {

    @Mock private ChatConversationLabelRepository labelRepository;
    @Mock private ChatConversationLabelAssignmentRepository assignmentRepository;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @InjectMocks private ChatLabelService service;

    private ChatConversation conv() {
        ChatConversation c = new ChatConversation();
        c.setId(1L);
        return c;
    }

    @Test
    void create_label_ok() {
        when(labelRepository.existsByUserIdAndNameIgnoreCase(10L, "Work")).thenReturn(false);
        when(labelRepository.countByUserId(10L)).thenReturn(0L);
        ChatConversationLabel l = ChatConversationLabel.builder().userId(10L).name("Work").color("#2563eb").build();
        when(labelRepository.save(any(ChatConversationLabel.class))).thenReturn(l);
        ChatLabelDto dto = service.create(10L, "  Work  ", "#2563eb");
        assertThat(dto.getName()).isEqualTo("Work");
    }

    @Test
    void assign_checksAccess() {
        ChatConversationLabel l = ChatConversationLabel.builder().userId(10L).name("Work").build();
        when(labelRepository.findByIdAndUserId(5L,10L)).thenReturn(Optional.of(l));
        when(conversationService.requireConversation(1L)).thenReturn(conv());
        when(assignmentRepository.findByLabelIdAndConversationId(5L,1L)).thenReturn(Optional.empty());
        service.assign(10L,5L,1L);
        verify(assignmentRepository).save(any());
    }

    @Test
    void list_returnsLabelsWithConversations() {
        ChatConversationLabel l = ChatConversationLabel.builder().userId(10L).name("Work").build();
        when(labelRepository.findByUserIdOrderByNameAsc(10L)).thenReturn(List.of(l));
        List<ChatLabelDto> list = service.list(10L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getName()).isEqualTo("Work");
    }
}