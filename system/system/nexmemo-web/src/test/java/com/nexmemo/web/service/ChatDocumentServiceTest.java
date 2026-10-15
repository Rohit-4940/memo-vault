package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMessageDocumentDto;
import com.nexmemo.core.dto.ShareDocumentRequest;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageDocument;
import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentStatus;
import com.nexmemo.core.repository.DocumentPermissionRepository;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.security.repository.ChatMessageDocumentRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.web.config.DocumentProperties;
import com.nexmemo.web.exception.DocumentPolicyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatDocumentServiceTest {

    @Mock private ChatMessageDocumentRepository shareRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private DocumentRecordRepository documentRepository;
    @Mock private DocumentPermissionRepository permissionRepository;
    @Mock private ChatConversationService conversationService;
    @Mock private ChatAuthorizationService chatAuth;
    @Mock(lenient=true) private DocumentAuthorizationService documentAuthz;
    @Mock private DocumentProperties documentProperties;
    @Mock private AuditEventPublisher auditEventPublisher;
    @InjectMocks private ChatDocumentService service;

    private ChatConversation conv() {
        ChatConversation c = new ChatConversation();
        c.setId(1L);
        c.setIsLocked(false);
        return c;
    }
    private ChatMessage msg() {
        ChatMessage m = new ChatMessage();
        m.setId(5L);
        m.setConversationId(1L);
        m.setIsDeleted(false);
        return m;
    }
    private DocumentRecord doc() {
        DocumentRecord d = new DocumentRecord();
        d.setId(100L);
        d.setIsDeleted(false);
        d.setOwnerId(10L);
        d.setTitle("Test");
        d.setOriginalFilename("test.pdf");
        d.setContentType("application/pdf");
        d.setSizeBytes(1024L);
        d.setSha256("abc");
        d.setClassification(DocumentClassification.INTERNAL);
        d.setStatus(DocumentStatus.ACTIVE);
        d.setCurrentVersion(1);
        return d;
    }

    @Test
    void share_succeedsWithGrant_byDefault() {
        when(conversationService.requireConversation(1L)).thenReturn(conv());
        when(messageRepository.findById(5L)).thenReturn(Optional.of(msg()));
        DocumentRecord d = doc();
        when(documentAuthz.requireLiveDocument(10L,100L)).thenReturn(d);
        DocumentAuthorizationService.Capabilities caps = new DocumentAuthorizationService.Capabilities(true,true,true,true);
        when(documentAuthz.capabilities(10L,d)).thenReturn(caps);
        when(shareRepository.findByMessageIdAndDocumentIdAndIsRevokedFalse(5L,100L)).thenReturn(Optional.empty());
        DocumentProperties.Share shareProps = new DocumentProperties.Share();
        shareProps.setAllowRestricted(false);
        shareProps.setAllowConfidential(false);
        when(documentProperties.getShare()).thenReturn(shareProps);
        ChatMessageDocument saved = ChatMessageDocument.builder()
                .messageId(5L).conversationId(1L).documentId(100L).sharedById(10L).grantedRead(true).isRevoked(false).messageId(5L).conversationId(1L).documentId(100L).sharedById(10L).grantedRead(true).isRevoked(false).build();
        ChatMessageDocument savedWithId = ChatMessageDocument.builder().messageId(5L).conversationId(1L).documentId(100L).sharedById(10L).grantedRead(true).isRevoked(false).build(); savedWithId.setId(200L);
        when(shareRepository.save(any(ChatMessageDocument.class))).thenReturn(savedWithId);
        when(documentRepository.findById(100L)).thenReturn(Optional.of(d));
        when(documentAuthz.isAdmin(10L)).thenReturn(false);
        ChatMessageDocumentDto dto = service.share(10L,1L,5L, new ShareDocumentRequest(100L,null,null,null));
        assertThat(dto.getId()).isEqualTo(200L);
        assertThat(dto.getRestricted()).isFalse();
        verify(permissionRepository).findByDocumentIdAndSubjectTypeAndSubjectId(eq(100L), any(), eq(1L));
    }

    @Test
    void share_blocksRestrictedByDefault() {
        when(conversationService.requireConversation(1L)).thenReturn(conv());
        when(messageRepository.findById(5L)).thenReturn(Optional.of(msg()));
        DocumentRecord d = doc();
        d.setClassification(DocumentClassification.RESTRICTED);
        when(documentAuthz.requireLiveDocument(10L,100L)).thenReturn(d);
        DocumentProperties.Share shareProps = new DocumentProperties.Share();
        shareProps.setAllowRestricted(false);
        shareProps.setAllowConfidential(false);
        when(documentProperties.getShare()).thenReturn(shareProps);
        assertThatThrownBy(() -> service.share(10L,1L,5L, new ShareDocumentRequest(100L,null,null,null)))
                .isInstanceOf(DocumentPolicyException.class);
    }
}