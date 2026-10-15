package com.nexmemo.web.service;

import com.nexmemo.core.dto.CreateShareLinkRequest;
import com.nexmemo.core.dto.DocumentDto;
import com.nexmemo.core.dto.DocumentShareLinkDto;
import com.nexmemo.core.dto.DocumentVersionDto;
import com.nexmemo.core.dto.DuplicateCheckResult;
import com.nexmemo.core.dto.GrantPermissionRequest;
import com.nexmemo.core.dto.ShareLinkLandingDto;
import com.nexmemo.core.entity.DocumentAuditEvent;
import com.nexmemo.core.entity.DocumentPermission;
import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.entity.DocumentShareLink;
import com.nexmemo.core.entity.DocumentVersion;
import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentStatus;
import com.nexmemo.core.enums.PermissionSubjectType;
import com.nexmemo.core.repository.DocumentAuditEventRepository;
import com.nexmemo.core.repository.DocumentPermissionRepository;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.core.repository.DocumentShareLinkRepository;
import com.nexmemo.core.repository.DocumentTagRepository;
import com.nexmemo.core.repository.DocumentVersionRepository;
import com.nexmemo.web.config.DocumentProperties;
import com.nexmemo.web.exception.DocumentPolicyException;
import com.nexmemo.web.storage.StorageService;
import com.nexmemo.web.utils.FileCompressionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the document vault service: Phase 2 (upload/versioning, download, soft delete,
 * permissions) and Enterprise Phase 4 (folders, status/legal hold, retention, quota, de-duplication,
 * version restore, share-landing and share policy).
 *
 * <p>Share-link <b>validation</b> lives in {@link ShareLinkGuard} and is covered by
 * {@code ShareLinkGuardTest}; here the guard is mocked and its denials are asserted to propagate.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRecordRepository documentRepository;
    @Mock private DocumentVersionRepository versionRepository;
    @Mock private DocumentPermissionRepository permissionRepository;
    @Mock private DocumentShareLinkRepository shareLinkRepository;
    @Mock private DocumentAuditEventRepository auditRepository;
    @Mock private DocumentTagRepository tagRepository;
    @Mock private StorageService storageService;
    @Mock private DocumentAuthorizationService authz;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private DocumentFolderService folderService;
    @Mock private DocumentQuotaService quotaService;
    @Mock private ShareLinkGuard shareLinkGuard;
    @Spy private DocumentProperties properties = new DocumentProperties();

    @InjectMocks private DocumentService service;

    private static final Long USER = 10L;
    private static final Long DOC_ID = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "shareBaseUrl", "http://localhost:4202");
        lenient().when(documentRepository.save(any())).thenAnswer(i -> {
            DocumentRecord d = i.getArgument(0);
            if (d.getId() == null) d.setId(500L);
            return d;
        });
        lenient().when(versionRepository.save(any())).thenAnswer(i -> {
            DocumentVersion v = i.getArgument(0);
            if (v.getId() == null) v.setId(900L);
            return v;
        });
        lenient().when(shareLinkRepository.save(any())).thenAnswer(i -> {
            DocumentShareLink l = i.getArgument(0);
            if (l.getId() == null) l.setId(700L);
            return l;
        });
        lenient().when(permissionRepository.save(any())).thenAnswer(i -> {
            DocumentPermission p = i.getArgument(0);
            if (p.getId() == null) p.setId(650L);
            return p;
        });
        lenient().when(tagRepository.findByDocumentId(any())).thenReturn(List.of());
    }

    private DocumentRecord liveDoc() {
        DocumentRecord d = new DocumentRecord();
        d.setId(DOC_ID);
        d.setOwnerId(USER);
        d.setIsDeleted(false);
        d.setCurrentVersion(1);
        d.setStatus(DocumentStatus.ACTIVE);
        return d;
    }

    private DocumentVersion version() {
        DocumentVersion v = new DocumentVersion();
        v.setId(900L);
        v.setDocumentId(DOC_ID);
        v.setVersionNo(1);
        v.setStorageKey("documents/10/uuid_file.pdf");
        v.setOriginalFilename("file.pdf");
        v.setContentType("application/pdf");
        v.setSizeBytes(11L);
        v.setSha256("abc");
        return v;
    }

    private MockMultipartFile file(String name, String type, String content) {
        return new MockMultipartFile("file", name, type, content.getBytes());
    }

    private static DocumentAuthorizationService.Capabilities capsRead() {
        return new DocumentAuthorizationService.Capabilities(true, false, false, false);
    }

    private static DocumentAuthorizationService.Capabilities capsNone() {
        return new DocumentAuthorizationService.Capabilities(false, false, false, false);
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @Test
    void upload_storesRawCreatesVersionAndAudits() {
        when(storageService.uploadRaw(eq("documents/10"), any(byte[].class), anyString()))
                .thenReturn("documents/10/uuid_contract.pdf");

        DocumentDto dto = service.upload(file("contract.pdf", "application/pdf", "hello world"),
                "Contract", "desc", DocumentClassification.INTERNAL, null, null, List.of("finance"), USER);

        assertThat(dto.getId()).isEqualTo(500L);
        assertThat(dto.getTitle()).isEqualTo("Contract");
        assertThat(dto.getOriginalFilename()).isEqualTo("contract.pdf");
        assertThat(dto.getSha256()).isNotBlank();
        verify(storageService).uploadRaw(eq("documents/10"), any(byte[].class), anyString());
        verify(versionRepository).save(any(DocumentVersion.class));
        verify(documentRepository).save(any(DocumentRecord.class));
        verify(quotaService).assertWithinQuota(eq(USER), anyLong());
        verify(auditRepository).save(any(DocumentAuditEvent.class));
        verify(auditEventPublisher).recordChange(eq(USER), eq("DOCUMENT_UPLOAD"), eq("DOCUMENT"), any(), any(), any());
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.upload(empty, null, null, null, null, null, null, USER))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void upload_reuseExistingSkipsStorageAndQuota() {
        String content = "duplicate payload";
        String sha = FileCompressionUtil.sha256(content.getBytes());
        DocumentRecord existing = new DocumentRecord();
        existing.setId(777L);
        existing.setOwnerId(USER);
        existing.setIsDeleted(false);
        existing.setStorageKey("documents/10/shared_key");
        when(documentRepository.findBySha256AndIsDeletedFalse(sha)).thenReturn(List.of(existing));
        when(authz.capabilities(USER, existing)).thenReturn(capsRead());

        DocumentDto dto = service.upload(file("copy.txt", "text/plain", content),
                null, null, DocumentClassification.INTERNAL, null, null, List.of(),
                null, true, USER);

        assertThat(dto.getSizeBytes()).isEqualTo((long) content.length());
        verify(storageService, never()).uploadRaw(anyString(), any(byte[].class), anyString());
        verify(quotaService, never()).assertWithinQuota(anyLong(), anyLong());
    }

    @Test
    void upload_rejectsFolderNotOwnedByCaller() {
        doThrow(new AccessDeniedException("nope")).when(folderService).requireOwned(USER, 55L);
        assertThatThrownBy(() -> service.upload(file("a.txt", "text/plain", "x"),
                null, null, null, null, null, List.of(), 55L, false, USER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storageService);
    }

    // ── Versioning ────────────────────────────────────────────────────────────

    @Test
    void addVersion_incrementsVersionAndKeepsHistory() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanWrite(USER, doc);
        when(storageService.uploadRaw(anyString(), any(byte[].class), anyString()))
                .thenReturn("documents/10/uuid_v2.pdf");

        DocumentVersionDto v = service.addVersion(DOC_ID, file("v2.pdf", "application/pdf", "v2"), "revision", USER);

        assertThat(v.getVersionNo()).isEqualTo(2);
        assertThat(doc.getCurrentVersion()).isEqualTo(2);
        verify(quotaService).assertWithinQuota(eq(USER), anyLong());
        verify(versionRepository).save(any(DocumentVersion.class));
    }

    @Test
    void addVersion_deniedWithoutWriteCapability() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doThrow(new AccessDeniedException("no")).when(authz).assertCanWrite(USER, doc);

        assertThatThrownBy(() -> service.addVersion(DOC_ID, file("v2.pdf", "application/pdf", "v2"), null, USER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void restoreVersion_appendsNewVersionReusingOldBytes() {
        DocumentRecord doc = liveDoc();
        doc.setCurrentVersion(3);
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanWrite(USER, doc);
        DocumentVersion old = version();
        old.setVersionNo(1);
        when(versionRepository.findByDocumentIdAndVersionNo(DOC_ID, 1)).thenReturn(Optional.of(old));

        DocumentVersionDto restored = service.restoreVersion(DOC_ID, 1, USER);

        assertThat(restored.getVersionNo()).isEqualTo(4);
        assertThat(doc.getCurrentVersion()).isEqualTo(4);
        assertThat(doc.getStorageKey()).isEqualTo(old.getStorageKey());
        // Bytes already exist, so the restore must not write new storage or charge quota.
        verifyNoInteractions(storageService);
        verify(quotaService, never()).assertWithinQuota(anyLong(), anyLong());
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @Test
    void download_streamsAuthorizedCurrentVersion() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanRead(USER, doc);
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version()));
        Resource resource = new ByteArrayResource("x".getBytes());
        when(storageService.download("documents/10/uuid_file.pdf")).thenReturn(resource);

        DocumentService.Download d = service.download(DOC_ID, USER, null);

        assertThat(d.filename()).isEqualTo("file.pdf");
        assertThat(d.resource()).isSameAs(resource);
    }

    @Test
    void download_deniedWhenCallerLacksRead() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doThrow(new AccessDeniedException("no")).when(authz).assertCanRead(USER, doc);

        assertThatThrownBy(() -> service.download(DOC_ID, USER, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storageService);
    }

    // ── Delete / retention / legal hold ───────────────────────────────────────

    @Test
    void delete_softDeletesAndRevokesShareLinks() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanDelete(USER, doc);
        DocumentShareLink link = new DocumentShareLink();
        link.setId(700L);
        link.setDocumentId(DOC_ID);
        link.setRevoked(false);
        when(shareLinkRepository.findByDocumentIdOrderByCreatedDateDesc(DOC_ID)).thenReturn(List.of(link));

        service.delete(DOC_ID, USER);

        assertThat(doc.getIsDeleted()).isTrue();
        assertThat(link.getRevoked()).isTrue();
        verify(auditEventPublisher).recordChange(eq(USER), eq("DOCUMENT_DELETE"), eq("DOCUMENT"), eq(DOC_ID), any(), any());
    }

    @Test
    void delete_blockedWhileUnderLegalHold() {
        DocumentRecord doc = liveDoc();
        doc.setStatus(DocumentStatus.LEGAL_HOLD);
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanDelete(USER, doc);

        assertThatThrownBy(() -> service.delete(DOC_ID, USER))
                .isInstanceOf(DocumentPolicyException.class)
                .hasMessageContaining("legal hold");
        assertThat(doc.getIsDeleted()).isFalse();
    }

    @Test
    void delete_blockedWhileRetentionNotElapsed() {
        DocumentRecord doc = liveDoc();
        doc.setRetentionUntil(LocalDateTime.now().plusDays(30));
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanDelete(USER, doc);

        assertThatThrownBy(() -> service.delete(DOC_ID, USER, false))
                .isInstanceOf(DocumentPolicyException.class)
                .hasMessageContaining("Retention");
    }

    @Test
    void delete_forceByAdminBypassesRetention() {
        DocumentRecord doc = liveDoc();
        doc.setRetentionUntil(LocalDateTime.now().plusDays(30));
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanDelete(USER, doc);
        when(authz.isAdmin(USER)).thenReturn(true);

        service.delete(DOC_ID, USER, true);

        assertThat(doc.getIsDeleted()).isTrue();
    }

    @Test
    void delete_forceByNonAdminRejected() {
        DocumentRecord doc = liveDoc();
        doc.setRetentionUntil(LocalDateTime.now().plusDays(30));
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanDelete(USER, doc);
        when(authz.isAdmin(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(DOC_ID, USER, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void setStatus_legalHoldRequiresAdmin() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        when(authz.isAdmin(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.setStatus(DOC_ID, DocumentStatus.LEGAL_HOLD, "case-1", USER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void setStatus_archivingClearsHoldReason() {
        DocumentRecord doc = liveDoc();
        doc.setHoldReason("old matter");
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanWrite(USER, doc);

        DocumentDto dto = service.setStatus(DOC_ID, DocumentStatus.ARCHIVED, null, USER);

        assertThat(dto.getStatus()).isEqualTo(DocumentStatus.ARCHIVED);
        assertThat(dto.getHoldReason()).isNull();
    }

    // ── Folders & de-duplication ──────────────────────────────────────────────

    @Test
    void moveDocument_requiresFolderOwnership() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanWrite(USER, doc);
        when(folderService.requireOwned(USER, 5L)).thenReturn(new com.nexmemo.core.entity.DocumentFolder());

        DocumentDto dto = service.moveDocument(DOC_ID, 5L, USER);

        assertThat(dto.getFolderId()).isEqualTo(5L);
        assertThat(doc.getFolderId()).isEqualTo(5L);
    }

    @Test
    void duplicates_returnsOnlyReadableMatches() {
        DocumentRecord readable = liveDoc();
        DocumentRecord hidden = new DocumentRecord();
        hidden.setId(200L);
        hidden.setOwnerId(99L);
        hidden.setIsDeleted(false);
        when(documentRepository.findBySha256AndIsDeletedFalse("abc")).thenReturn(List.of(readable, hidden));
        when(authz.capabilities(USER, readable)).thenReturn(capsRead());
        when(authz.capabilities(USER, hidden)).thenReturn(capsNone());

        List<DocumentDto> matches = service.duplicates(USER, "abc");

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getId()).isEqualTo(DOC_ID);
    }

    @Test
    void checkDuplicate_reportsDigestAndMatches() {
        String content = "same bytes";
        String sha = FileCompressionUtil.sha256(content.getBytes());
        when(documentRepository.findBySha256AndIsDeletedFalse(sha)).thenReturn(List.of());

        DuplicateCheckResult result = service.checkDuplicate(file("a.bin", "application/octet-stream", content), USER);

        assertThat(result.getSha256()).isEqualTo(sha);
        assertThat(result.getMatches()).isEmpty();
    }

    // ── Permissions / share links (management) ────────────────────────────────

    @Test
    void grant_requiresShareCapability() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doThrow(new AccessDeniedException("no")).when(authz).assertCanShare(USER, doc);

        GrantPermissionRequest req = new GrantPermissionRequest();
        req.setSubjectType(PermissionSubjectType.USER);
        req.setSubjectId(20L);

        assertThatThrownBy(() -> service.grant(DOC_ID, req, USER)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createShareLink_returnsTokenUrlAndExpiry() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanShare(USER, doc);
        CreateShareLinkRequest req = new CreateShareLinkRequest();
        req.setExpiresInHours(24);
        req.setMaxDownloads(3);

        DocumentShareLinkDto dto = service.createShareLink(DOC_ID, req, USER);

        assertThat(dto.getToken()).isNotBlank();
        assertThat(dto.getShareUrl()).startsWith("http://localhost:4202/documents/shared/");
        assertThat(dto.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(dto.getPasswordProtected()).isFalse();
    }

    @Test
    void createShareLink_hashesOptionalPassword() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanShare(USER, doc);
        when(passwordEncoder.encode("secret")).thenReturn("HASHED");
        CreateShareLinkRequest req = new CreateShareLinkRequest();
        req.setPassword("secret");

        DocumentShareLinkDto dto = service.createShareLink(DOC_ID, req, USER);

        assertThat(dto.getPasswordProtected()).isTrue();
        verify(passwordEncoder).encode("secret");
    }

    @Test
    void createShareLink_blockedForRestrictedByDefault() {
        DocumentRecord doc = liveDoc();
        doc.setClassification(DocumentClassification.RESTRICTED);
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanShare(USER, doc);

        assertThatThrownBy(() -> service.createShareLink(DOC_ID, new CreateShareLinkRequest(), USER))
                .isInstanceOf(DocumentPolicyException.class)
                .hasMessageContaining("RESTRICTED");
    }

    @Test
    void createShareLink_allowedForRestrictedWhenPolicyPermits() {
        properties.getShare().setAllowRestricted(true);
        DocumentRecord doc = liveDoc();
        doc.setClassification(DocumentClassification.RESTRICTED);
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanShare(USER, doc);

        DocumentShareLinkDto dto = service.createShareLink(DOC_ID, new CreateShareLinkRequest(), USER);

        assertThat(dto.getToken()).isNotBlank();
    }

    @Test
    void revokeShareLink_marksRevoked() {
        DocumentRecord doc = liveDoc();
        when(authz.requireLiveDocument(USER, DOC_ID)).thenReturn(doc);
        doNothing().when(authz).assertCanShare(USER, doc);
        DocumentShareLink link = new DocumentShareLink();
        link.setId(700L);
        link.setDocumentId(DOC_ID);
        link.setRevoked(false);
        when(shareLinkRepository.findById(700L)).thenReturn(Optional.of(link));

        service.revokeShareLink(DOC_ID, 700L, USER);

        assertThat(link.getRevoked()).isTrue();
    }

    // ── Share-link redemption (public) ────────────────────────────────────────

    private DocumentShareLink link() {
        DocumentShareLink l = new DocumentShareLink();
        l.setId(700L);
        l.setDocumentId(DOC_ID);
        l.setToken("tok");
        l.setRevoked(false);
        l.setMaxDownloads(5);
        l.setDownloadCount(0);
        return l;
    }

    @Test
    void redeemShareLink_happyPath_incrementsCounter() {
        DocumentShareLink l = link();
        when(shareLinkGuard.validate("tok", null)).thenReturn(l);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(liveDoc()));
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version()));
        when(storageService.download("documents/10/uuid_file.pdf")).thenReturn(new ByteArrayResource("x".getBytes()));

        DocumentService.Download d = service.redeemShareLink("tok", null);

        assertThat(l.getDownloadCount()).isEqualTo(1);
        assertThat(d.filename()).isEqualTo("file.pdf");
    }

    @Test
    void redeemShareLink_guardDenialPropagates() {
        when(shareLinkGuard.validate("tok", "bad")).thenThrow(new AccessDeniedException("Share link has expired"));
        assertThatThrownBy(() -> service.redeemShareLink("tok", "bad"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void shareLanding_reportsPasswordExpiryAndRemaining() {
        DocumentShareLink l = link();
        l.setExpiresAt(LocalDateTime.now().plusHours(2));
        l.setMaxDownloads(5);
        l.setDownloadCount(2);
        when(shareLinkGuard.load("tok")).thenReturn(l);
        when(shareLinkGuard.passwordRequired(l)).thenReturn(true);
        when(shareLinkGuard.remaining(l)).thenReturn(3);
        when(documentRepository.findById(DOC_ID)).thenReturn(Optional.of(liveDoc()));
        when(versionRepository.findByDocumentIdOrderByVersionNoDesc(DOC_ID)).thenReturn(List.of(version()));

        ShareLinkLandingDto landing = service.shareLanding("tok");

        assertThat(landing.getFilename()).isEqualTo("file.pdf");
        assertThat(landing.isPasswordRequired()).isTrue();
        assertThat(landing.getRemainingDownloads()).isEqualTo(3);
    }
}
