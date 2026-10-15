package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatDraftDto;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatDraft;
import com.nexmemo.security.repository.ChatDraftRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatDraftServiceTest {

    @Mock private ChatDraftRepository draftRepository;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @InjectMocks private ChatDraftService service;

    private ChatConversation conv() {
        ChatConversation c = new ChatConversation();
        c.setId(1L);
        return c;
    }

    @Test
    void save_createsDraft_whenBodyProvided() {
        when(conversationService.requireConversation(1L)).thenReturn(conv());
        when(draftRepository.findByConversationIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        ChatDraft saved = new ChatDraft();
        saved.setId(5L);
        saved.setConversationId(1L);
        saved.setUserId(10L);
        saved.setBody("hi");
        saved.setModifiedDate(LocalDateTime.of(2026,10,13,10,0));
        when(draftRepository.save(any(ChatDraft.class))).thenReturn(saved);
        ChatDraftDto dto = service.save(10L, 1L, "hi");
        assertThat(dto.getConversationId()).isEqualTo(1L);
        assertThat(dto.getBody()).isEqualTo("hi");
        verify(draftRepository).save(any(ChatDraft.class));
    }

    @Test
    void save_clearsDraft_whenBlank() {
        when(conversationService.requireConversation(1L)).thenReturn(conv());
        service.save(10L, 1L, "  ");
        verify(draftRepository).deleteByConversationIdAndUserId(1L, 10L);
        verify(draftRepository, never()).save(any(ChatDraft.class));
    }

    @Test
    void get_returnsExistingDraft() {
        when(conversationService.requireConversation(1L)).thenReturn(conv());
        ChatDraft d = new ChatDraft();
        d.setConversationId(1L);
        d.setUserId(10L);
        d.setBody("hello");
        d.setModifiedDate(LocalDateTime.of(2026,10,13,9,0));
        when(draftRepository.findByConversationIdAndUserId(1L,10L)).thenReturn(Optional.of(d));
        ChatDraftDto dto = service.get(10L, 1L);
        assertThat(dto.getBody()).isEqualTo("hello");
        assertThat(dto.getUpdatedAt()).isNotNull();
    }
}