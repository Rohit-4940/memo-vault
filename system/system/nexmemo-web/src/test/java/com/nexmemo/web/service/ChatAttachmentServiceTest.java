package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatAttachmentDto;
import com.nexmemo.core.entity.ChatAttachment;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.enums.ChatAttachmentScanStatus;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.security.repository.ChatAttachmentRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for secure attachment handling: whitelist, size cap, malware verdicts
 * (disabled scanner = pass-through, infected = reject) and authorized download.
 */
@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock private ChatAttachmentRepository attachmentRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock private ClamAvScanner clamAvScanner;

    @InjectMocks private ChatAttachmentService service;

    private static final Long USER = 10L;
    private static final Long CONVERSATION = 1L;
    private static final Long MESSAGE = 50L;

    private ChatConversation conversation;
    private ChatMessage message;

    @BeforeEach
    void setUp() {
        conversation = new ChatConversation();
        conversation.setId(CONVERSATION);
        conversation.setConversationType(ChatConversationType.GROUP);
        conversation.setIsLocked(false);

        message = ChatMessage.builder()
                .id(MESSAGE)
                .conversationId(CONVERSATION)
                .senderId(USER)
                .isDeleted(false)
                .build();

        lenient().when(conversationService.requireConversation(CONVERSATION)).thenReturn(conversation);
        lenient().when(messageRepository.findById(MESSAGE)).thenReturn(Optional.of(message));
    }

    private MultipartFile file(String name, String type, byte[] bytes) {
        return new MockMultipartFile("file", name, type, bytes);
    }

    @Test
    void upload_rejectsDisallowedExtension() {
        assertThatThrownBy(() -> service.upload(USER, CONVERSATION, MESSAGE,
                file("script.exe", "application/octet-stream", new byte[]{1})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_rejectsOversizedFile() {
        byte[] big = new byte[(int) ChatAttachmentService.MAX_SIZE_BYTES + 1];
        assertThatThrownBy(() -> service.upload(USER, CONVERSATION, MESSAGE,
                file("doc.pdf", "application/pdf", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("25 MB");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_scanDisabled_storesAsClean() {
        
        when(clamAvScanner.isEnabled()).thenReturn(false);
        when(attachmentRepository.save(any(ChatAttachment.class))).thenAnswer(inv -> {
            ChatAttachment a = inv.getArgument(0);
            a.setId(77L);
            return a;
        });

        ChatAttachmentDto dto = service.upload(USER, CONVERSATION, MESSAGE,
                file("memo.pdf", "application/pdf", "hello".getBytes()));

        assertThat(dto.getId()).isEqualTo(77L);
        assertThat(dto.getScanStatus()).isEqualTo("CLEAN");
        assertThat(dto.getFileName()).isEqualTo("memo.pdf");
        assertThat(dto.getSha256()).hasSize(64);
        verify(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));
    }

    @Test
    void upload_infectedScan_rejectsAndStoresNothing() {
        
        when(clamAvScanner.isEnabled()).thenReturn(true);
        when(clamAvScanner.scan(any(byte[].class))).thenReturn(false);

        assertThatThrownBy(() -> service.upload(USER, CONVERSATION, MESSAGE,
                file("bad.pdf", "application/pdf", "bad".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malware");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_scannerDown_failsClosed() {
        
        when(clamAvScanner.isEnabled()).thenReturn(true);
        when(clamAvScanner.scan(any(byte[].class)))
                .thenThrow(new IllegalStateException("Malware scanner unavailable; upload rejected"));

        assertThatThrownBy(() -> service.upload(USER, CONVERSATION, MESSAGE,
                file("doc.pdf", "application/pdf", "x".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malware scanner unavailable");
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_deniedWithoutConversationAccess() {
        doThrow(new AccessDeniedException("nope"))
                .when(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));

        assertThatThrownBy(() -> service.upload(USER, CONVERSATION, MESSAGE,
                file("doc.pdf", "application/pdf", "x".getBytes())))
                .isInstanceOf(AccessDeniedException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void upload_rejectsMessageMismatch() {
        
        ChatMessage other = ChatMessage.builder()
                .id(MESSAGE).conversationId(999L).isDeleted(false).build();
        when(messageRepository.findById(MESSAGE)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.upload(USER, CONVERSATION, MESSAGE,
                file("doc.pdf", "application/pdf", "x".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void loadAuthorized_requiresConversationAccess_andCleanScan() {
        ChatAttachment attachment = ChatAttachment.builder()
                .messageId(MESSAGE).conversationId(CONVERSATION)
                .fileName("a.pdf").scanStatus(ChatAttachmentScanStatus.CLEAN)
                .fileData(new byte[]{1, 2, 3}).build();
        attachment.setId(1L);
        when(attachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        ChatAttachment loaded = service.loadAuthorized(USER, 1L);

        assertThat(loaded.getFileData()).containsExactly(1, 2, 3);
        verify(chatAuth).assertCanAccessConversation(eq(USER), any(ChatConversation.class));
    }

    @Test
    void loadAuthorized_refusesUnscannedFile() {
        ChatAttachment attachment = ChatAttachment.builder()
                .messageId(MESSAGE).conversationId(CONVERSATION)
                .fileName("a.pdf").scanStatus(ChatAttachmentScanStatus.PENDING)
                .fileData(new byte[]{1}).build();
        attachment.setId(1L);
        when(attachmentRepository.findById(1L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.loadAuthorized(USER, 1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void sanitizeFileName_stripsPathsAndControls() {
        assertThat(ChatAttachmentService.sanitizeFileName("..\\..\\evil.txt")).isEqualTo(".._.._evil.txt");
        assertThat(ChatAttachmentService.sanitizeFileName("a/b/c.pdf")).isEqualTo("a_b_c.pdf");
        assertThat(ChatAttachmentService.sanitizeFileName(null)).isNull();
        assertThat(ChatAttachmentService.sanitizeFileName("   ")).isNull();
    }
}