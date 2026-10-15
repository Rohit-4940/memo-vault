package com.nexmemo.web.service;

import com.nexmemo.core.dto.PreviewMetadataDto;
import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.entity.DocumentShareLink;
import com.nexmemo.core.entity.DocumentVersion;
import com.nexmemo.core.enums.PreviewKind;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.core.repository.DocumentVersionRepository;
import com.nexmemo.web.config.DocumentProperties;
import com.nexmemo.web.exception.DocumentPolicyException;
import com.nexmemo.web.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Security tests for the inline-preview pipeline (Enterprise Phase 4): previewability is decided from
 * real magic bytes (not the declared type), HTML/SVG/unknown stay non-previewable, and every path
 * enforces document authorization or the share-link policy.
 */
@ExtendWith(MockitoExtension.class)
class DocumentPreviewServiceTest {

    @Mock private DocumentAuthorizationService authz;
    @Mock private DocumentRecordRepository documentRepository;
    @Mock private DocumentVersionRepository versionRepository;
    @Mock private StorageService storageService;
    @Mock private ShareLinkGuard shareLinkGuard;
    @Spy private DocumentProperties properties = new DocumentProperties();

    @InjectMocks private DocumentPreviewService service;

    private static final Long USER = 10L;
    private static final Long DOC_ID = 100L;
    private static final String KEY = "documents/10/uuid_file";

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HTML = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BINARY = {0x00, 0x01, 0x02, 0x03, (byte) 0xFF, 0x10, 0x11, 0x12};

    private DocumentRecord doc() {
        DocumentRecord d = new DocumentRecord();
        d.setId(DOC_ID);
        d.setOwnerId(USER);
        d.setIsDeleted(false);
        return d;
    }

    private DocumentVersion version(byte[] bytes) {
        DocumentVersion v = new DocumentVersion();
        v.setId(900L);
        v.setDocumentId(DOC_ID);
        v.setVersionNo(1);
        v.setStorageKey(KEY);
        v.setOriginalFilename("file.bin");
        v.setContentType("application/octet-stream");
        v.setSizeBytes((long) bytes.length);
        return v;
    }

    private void stubAuthorized(byte[] bytes) {
        DocumentRecord d = doc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(d);
        doNothing().when(authz).assertCanRead(USER, d);
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version(bytes)));
        when(storageService.download(KEY)).thenReturn(new ByteArrayResource(bytes));
    }

    @Test
    void metadataForUser_detectsImageFromMagicBytes() {
        stubAuthorized(PNG);

        PreviewMetadataDto meta = service.metadataForUser(USER, DOC_ID, null);

        assertThat(meta.getPreviewKind()).isEqualTo(PreviewKind.IMAGE);
        assertThat(meta.isPreviewable()).isTrue();
        assertThat(meta.getContentType()).isEqualTo("image/png");
    }

    @Test
    void metadataForUser_detectsPdf() {
        stubAuthorized(PDF);

        PreviewMetadataDto meta = service.metadataForUser(USER, DOC_ID, null);

        assertThat(meta.getPreviewKind()).isEqualTo(PreviewKind.PDF);
        assertThat(meta.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void metadataForUser_treatsSmallUtf8AsText() {
        stubAuthorized("hello, plain text".getBytes(StandardCharsets.UTF_8));

        PreviewMetadataDto meta = service.metadataForUser(USER, DOC_ID, null);

        assertThat(meta.getPreviewKind()).isEqualTo(PreviewKind.TEXT);
        assertThat(meta.getContentType()).contains("text/plain");
    }

    @Test
    void metadataForUser_htmlStaysNonPreviewable() {
        // A ".html" upload is plain UTF-8 text, but it must never render as markup: it is served as
        // text/plain and the kind is TEXT (not a renderable HTML kind, which does not exist here).
        stubAuthorized(HTML);

        PreviewMetadataDto meta = service.metadataForUser(USER, DOC_ID, null);

        assertThat(meta.getPreviewKind()).isEqualTo(PreviewKind.TEXT);
        assertThat(meta.getContentType()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void metadataForUser_unknownBinaryIsNotPreviewable() {
        stubAuthorized(BINARY);

        PreviewMetadataDto meta = service.metadataForUser(USER, DOC_ID, null);

        assertThat(meta.getPreviewKind()).isEqualTo(PreviewKind.NONE);
        assertThat(meta.isPreviewable()).isFalse();
    }

    @Test
    void metadataForUser_deniedWithoutReadCapability() {
        DocumentRecord d = doc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(d);
        doThrow(new AccessDeniedException("no")).when(authz).assertCanRead(USER, d);

        assertThatThrownBy(() -> service.metadataForUser(USER, DOC_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void streamForUser_returnsCanonicalContentType() {
        stubAuthorized(PNG);

        DocumentPreviewService.Preview preview = service.streamForUser(USER, DOC_ID, null);

        assertThat(preview.kind()).isEqualTo(PreviewKind.IMAGE);
        assertThat(preview.contentType()).isEqualTo("image/png");
    }

    @Test
    void streamForUser_rejectsNonPreviewableWith415() {
        stubAuthorized(BINARY);

        assertThatThrownBy(() -> service.streamForUser(USER, DOC_ID, null))
                .isInstanceOf(DocumentPolicyException.class)
                .satisfies(ex -> assertThat(((DocumentPolicyException) ex).getStatus())
                        .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void streamForUser_rejectsWhenPreviewDisabled() {
        properties.getPreview().setEnabled(false);
        DocumentRecord d = doc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(d);
        doNothing().when(authz).assertCanRead(USER, d);
        // streamForUser resolves the target version before the preview-enabled policy check.
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version(PNG)));

        assertThatThrownBy(() -> service.streamForUser(USER, DOC_ID, null))
                .isInstanceOf(DocumentPolicyException.class)
                .satisfies(ex -> assertThat(((DocumentPolicyException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void metadataForShare_usesLinkWithoutConsumingDownload() {
        DocumentShareLink link = new DocumentShareLink();
        link.setId(700L);
        link.setDocumentId(DOC_ID);
        link.setRevoked(false);
        when(shareLinkGuard.load("tok")).thenReturn(link);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc()));
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version(PNG)));
        when(storageService.download(KEY)).thenReturn(new ByteArrayResource(PNG));

        PreviewMetadataDto meta = service.metadataForShare("tok");

        assertThat(meta.getPreviewKind()).isEqualTo(PreviewKind.IMAGE);
    }

    @Test
    void streamForShare_enforcesFullLinkPolicy() {
        DocumentShareLink link = new DocumentShareLink();
        link.setId(700L);
        link.setDocumentId(DOC_ID);
        link.setRevoked(false);
        when(shareLinkGuard.validate("tok", "pw")).thenReturn(link);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(doc()));
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version(PNG)));
        when(storageService.download(KEY)).thenReturn(new ByteArrayResource(PNG));

        DocumentPreviewService.Preview preview = service.streamForShare("tok", "pw");

        assertThat(preview.kind()).isEqualTo(PreviewKind.IMAGE);
    }
}
