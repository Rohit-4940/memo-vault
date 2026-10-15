package com.nexmemo.web.service;

import com.nexmemo.core.dto.CreateShareLinkRequest;
import com.nexmemo.core.dto.DocumentAuditEventDto;
import com.nexmemo.core.dto.DocumentDto;
import com.nexmemo.core.dto.DocumentPermissionDto;
import com.nexmemo.core.dto.DocumentShareLinkDto;
import com.nexmemo.core.dto.DocumentVersionDto;
import com.nexmemo.core.dto.DuplicateCheckResult;
import com.nexmemo.core.dto.GrantPermissionRequest;
import com.nexmemo.core.dto.ShareLinkLandingDto;
import com.nexmemo.core.entity.DocumentAuditEvent;
import com.nexmemo.core.entity.DocumentPermission;
import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.entity.DocumentShareLink;
import com.nexmemo.core.entity.DocumentTag;
import com.nexmemo.core.entity.DocumentVersion;
import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentSourceType;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Document vault service (Phase 2 — document foundation).
 *
 * <p>Owns the canonical document lifecycle: upload, versioning, metadata, permissions, share links,
 * authorized download and the per-document audit trail. Every operation first resolves the caller's
 * capabilities through {@link DocumentAuthorizationService}; the client is never trusted.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentService {

    /** Hard ceiling mirroring {@code nexmemo.upload.max-size}. */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DocumentRecordRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final DocumentShareLinkRepository shareLinkRepository;
    private final DocumentAuditEventRepository auditRepository;
    private final DocumentTagRepository tagRepository;
    private final StorageService storageService;
    private final DocumentAuthorizationService authz;
    private final AuditEventPublisher auditEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final DocumentFolderService folderService;
    private final DocumentQuotaService quotaService;
    private final ShareLinkGuard shareLinkGuard;
    private final DocumentProperties properties;

    @Value("${nexmemo.documents.share-base-url:http://localhost:4202}")
    private String shareBaseUrl;

    /** A resolved, authorized download payload. */
    public record Download(Resource resource, String filename, String contentType, Long sizeBytes) {
    }

    // ── Upload / versioning ───────────────────────────────────────────────────

    public DocumentDto upload(MultipartFile file, String title, String description,
                              DocumentClassification classification, Long branchId, Long departmentId,
                              List<String> tags, Long userId) {
        return upload(file, title, description, classification, branchId, departmentId, tags,
                null, false, userId);
    }

    /**
     * Upload a new document into the vault.
     *
     * @param folderId      optional organisational folder (ownership validated) — never affects access
     * @param reuseExisting when true and readable byte-identical content already exists, the new record
     *                      shares the existing storage key instead of writing the bytes again (hash
     *                      de-duplication)
     */
    public DocumentDto upload(MultipartFile file, String title, String description,
                              DocumentClassification classification, Long branchId, Long departmentId,
                              List<String> tags, Long folderId, boolean reuseExisting, Long userId) {
        byte[] bytes = readBytes(file);
        String originalName = safeName(file.getOriginalFilename());
        String sha = FileCompressionUtil.sha256(bytes);

        Long resolvedFolderId = folderId;
        if (folderId != null) {
            folderService.requireOwned(userId, folderId);
        }

        String storageKey = null;
        if (reuseExisting) {
            storageKey = findReadableDuplicate(userId, sha).map(DocumentRecord::getStorageKey).orElse(null);
        }
        if (storageKey == null) {
            quotaService.assertWithinQuota(userId, bytes.length);
            storageKey = store(bytes, originalName, userId);
        }

        DocumentRecord doc = new DocumentRecord();
        doc.setStorageKey(storageKey);
        doc.setOriginalFilename(originalName);
        doc.setContentType(file.getContentType());
        doc.setSizeBytes((long) bytes.length);
        doc.setSha256(sha);
        doc.setTitle(title == null || title.isBlank() ? originalName : title);
        doc.setDescription(description);
        doc.setOwnerId(userId);
        doc.setClassification(classification == null ? DocumentClassification.INTERNAL : classification);
        doc.setBranchId(branchId);
        doc.setDepartmentId(departmentId);
        doc.setFolderId(resolvedFolderId);
        doc.setCurrentVersion(1);
        doc.setSourceType(DocumentSourceType.UPLOAD);
        doc.setIsDeleted(false);
        doc = documentRepository.save(doc);

        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setVersionNo(1);
        version.setStorageKey(storageKey);
        version.setOriginalFilename(originalName);
        version.setContentType(file.getContentType());
        version.setSizeBytes((long) bytes.length);
        version.setSha256(sha);
        version.setNotes("Initial version");
        version.setUploadedById(userId);
        versionRepository.save(version);

        replaceTags(doc.getId(), tags);
        audit(doc.getId(), "UPLOAD", userId, Map.of("filename", originalName, "size", bytes.length), "SUCCESS");
        auditEventPublisher.recordChange(userId, "DOCUMENT_UPLOAD", "DOCUMENT", doc.getId(), null,
                Map.of("filename", originalName, "sha256", String.valueOf(sha)));

        return toDto(doc, tagsFor(doc.getId()));
    }

    public DocumentVersionDto addVersion(Long documentId, MultipartFile file, String notes, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanWrite(userId, doc);

        byte[] bytes = readBytes(file);
        String originalName = safeName(file.getOriginalFilename());
        quotaService.assertWithinQuota(userId, bytes.length);
        String storageKey = store(bytes, originalName, userId);
        String sha = FileCompressionUtil.sha256(bytes);

        int next = (doc.getCurrentVersion() == null ? 1 : doc.getCurrentVersion()) + 1;
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setVersionNo(next);
        version.setStorageKey(storageKey);
        version.setOriginalFilename(originalName);
        version.setContentType(file.getContentType());
        version.setSizeBytes((long) bytes.length);
        version.setSha256(sha);
        version.setNotes(notes);
        version.setUploadedById(userId);
        version = versionRepository.save(version);

        doc.setCurrentVersion(next);
        doc.setStorageKey(storageKey);
        doc.setOriginalFilename(originalName);
        doc.setContentType(file.getContentType());
        doc.setSizeBytes((long) bytes.length);
        doc.setSha256(sha);
        documentRepository.save(doc);

        audit(doc.getId(), "NEW_VERSION", userId, Map.of("version", next, "sha256", String.valueOf(sha)), "SUCCESS");
        return toVersionDto(version);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DocumentDto> list(Long userId, String search, Pageable pageable) {
        return list(userId, search, null, null, null, null, pageable);
    }

    /**
     * Catalogue search: the readable scope is always applied first; the filters below only narrow it
     * further, so they can never widen access. A folder filter additionally verifies folder ownership
     * so a caller cannot probe another user's folder ids.
     */
    @Transactional(readOnly = true)
    public Page<DocumentDto> list(Long userId, String search, Long folderId,
                                  DocumentClassification classification, DocumentStatus status,
                                  String tag, Pageable pageable) {
        if (folderId != null) {
            folderService.requireOwned(userId, folderId);
        }
        DocumentAuthorizationService.AccessScope scope = authz.scopeFor(userId);
        String q = (search == null || search.isBlank()) ? null : search.trim();
        String tagFilter = (tag == null || tag.isBlank()) ? null : tag.trim();
        Page<DocumentRecord> page = documentRepository.findReadable(
                scope.userId(), scope.admin(), q, folderId, classification, status, tagFilter,
                PermissionSubjectType.USER, PermissionSubjectType.ROLE,
                PermissionSubjectType.BRANCH, PermissionSubjectType.DEPARTMENT,
                PermissionSubjectType.CONVERSATION,
                nonEmpty(scope.roleIds()), nonEmpty(scope.branchIds()), nonEmpty(scope.departmentIds()),
                nonEmpty(scope.conversationIds()),
                pageable);

        List<Long> ids = page.getContent().stream().map(DocumentRecord::getId).toList();
        Map<Long, List<String>> tags = ids.isEmpty() ? Map.of()
                : tagRepository.findByDocumentIdIn(ids).stream()
                    .collect(Collectors.groupingBy(DocumentTag::getDocumentId,
                            Collectors.mapping(DocumentTag::getTag, Collectors.toList())));

        return page.map(d -> toDto(d, tags.getOrDefault(d.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public DocumentDto get(Long documentId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        return toDto(doc, tagsFor(doc.getId()));
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionDto> versions(Long documentId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        return versionRepository.findByDocumentIdOrderByVersionNoDesc(documentId).stream()
                .map(this::toVersionDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentPermissionDto> permissions(Long documentId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        return permissionRepository.findByDocumentId(documentId).stream().map(this::toPermissionDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentShareLinkDto> shareLinks(Long documentId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanShare(userId, doc);
        return shareLinkRepository.findByDocumentIdOrderByCreatedDateDesc(documentId).stream()
                .map(l -> toShareLinkDto(l, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentAuditEventDto> auditTrail(Long documentId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        return auditRepository.findByDocumentIdOrderByCreatedDateDesc(documentId).stream()
                .map(this::toAuditDto).toList();
    }

    // ── Authorized download ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Download download(Long documentId, Long userId, Integer versionNo) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        DocumentVersion version = resolveVersion(doc, versionNo);
        Resource resource = storageService.download(version.getStorageKey());
        audit(documentId, "DOWNLOAD", userId, Map.of("version", version.getVersionNo()), "SUCCESS");
        return new Download(resource, version.getOriginalFilename(), version.getContentType(), version.getSizeBytes());
    }

    /** Public (token-gated) download of a share link — no session required. */
    public Download redeemShareLink(String token, String password) {
        DocumentShareLink link = shareLinkGuard.validate(token, password);
        DocumentRecord doc = documentRepository.findById(link.getDocumentId())
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .orElseThrow(() -> new AccessDeniedException("Document is no longer available"));

        link.setDownloadCount((link.getDownloadCount() == null ? 0 : link.getDownloadCount()) + 1);
        shareLinkRepository.save(link);

        DocumentVersion version = resolveVersion(doc, null);
        Resource resource = storageService.download(version.getStorageKey());
        audit(doc.getId(), "SHARE_DOWNLOAD", null, Map.of("linkId", link.getId()), "SUCCESS");
        return new Download(resource, version.getOriginalFilename(), version.getContentType(), version.getSizeBytes());
    }

    /**
     * Public landing metadata for a share link. Confirms the link is live (not revoked/expired) and
     * reports whether a password is needed and how many downloads remain — without revealing the
     * document id, owner, classification or any bytes, and without consuming a download.
     */
    @Transactional(readOnly = true)
    public ShareLinkLandingDto shareLanding(String token) {
        DocumentShareLink link = shareLinkGuard.load(token);
        DocumentRecord doc = documentRepository.findById(link.getDocumentId())
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .orElseThrow(() -> new AccessDeniedException("Document is no longer available"));
        DocumentVersion version = resolveVersion(doc, null);
        return ShareLinkLandingDto.builder()
                .filename(version.getOriginalFilename())
                .sizeBytes(version.getSizeBytes())
                .contentType(version.getContentType())
                .passwordRequired(shareLinkGuard.passwordRequired(link))
                .expiresAt(link.getExpiresAt())
                .remainingDownloads(shareLinkGuard.remaining(link))
                .build();
    }

    // ── Metadata / delete ─────────────────────────────────────────────────────

    public DocumentDto updateMetadata(Long documentId, String title, String description,
                                      DocumentClassification classification, List<String> tags, Long userId) {
        return updateMetadata(documentId, title, description, classification, tags, null, userId);
    }

    public DocumentDto updateMetadata(Long documentId, String title, String description,
                                      DocumentClassification classification, List<String> tags,
                                      LocalDateTime retentionUntil, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanWrite(userId, doc);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("title", doc.getTitle());
        before.put("description", doc.getDescription());
        before.put("classification", String.valueOf(doc.getClassification()));
        before.put("retentionUntil", String.valueOf(doc.getRetentionUntil()));

        if (title != null) doc.setTitle(title);
        if (description != null) doc.setDescription(description);
        if (classification != null) doc.setClassification(classification);
        if (retentionUntil != null) doc.setRetentionUntil(retentionUntil);
        documentRepository.save(doc);
        if (tags != null) replaceTags(documentId, tags);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("title", doc.getTitle());
        after.put("description", doc.getDescription());
        after.put("classification", String.valueOf(doc.getClassification()));
        after.put("retentionUntil", String.valueOf(doc.getRetentionUntil()));
        auditEventPublisher.recordChange(userId, "DOCUMENT_UPDATE", "DOCUMENT", documentId, before, after);
        audit(documentId, "UPDATE_METADATA", userId, null, "SUCCESS");

        return toDto(doc, tagsFor(documentId));
    }

    public void delete(Long documentId, Long userId) {
        delete(documentId, userId, false);
    }

    /**
     * Soft-delete a document and immediately invalidate every share link so it cannot outlive the
     * document. Retention and legal hold are enforced here: a document under legal hold can never be
     * deleted, and a document whose retention window has not elapsed can only be force-deleted by an
     * administrator.
     */
    public void delete(Long documentId, Long userId, boolean force) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanDelete(userId, doc);
        if (DocumentStatus.LEGAL_HOLD.equals(doc.getStatus())) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN,
                    "Document is under legal hold and cannot be deleted");
        }
        if (force && !authz.isAdmin(userId)) {
            throw new AccessDeniedException("Only an administrator may force-delete a retained document");
        }
        if (!force && doc.getRetentionUntil() != null && LocalDateTime.now().isBefore(doc.getRetentionUntil())) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN,
                    "Retention period has not elapsed; only an administrator may force-delete");
        }
        doc.setIsDeleted(true);
        documentRepository.save(doc);
        // Immediately invalidate every share link so it cannot outlive the document.
        shareLinkRepository.findByDocumentIdOrderByCreatedDateDesc(documentId).forEach(l -> {
            l.setRevoked(true);
            shareLinkRepository.save(l);
        });
        audit(documentId, "DELETE", userId, Map.of("force", force), "SUCCESS");
        auditEventPublisher.recordChange(userId, "DOCUMENT_DELETE", "DOCUMENT", documentId,
                Map.of("isDeleted", false), Map.of("isDeleted", true));
    }

    // ── Catalogue organisation, lifecycle, versioning & de-duplication ────────

    /** Moves a document into one of the caller's folders (or to uncategorised when {@code folderId} is null). */
    public DocumentDto moveDocument(Long documentId, Long folderId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanWrite(userId, doc);
        if (folderId != null) {
            folderService.requireOwned(userId, folderId);
        }
        doc.setFolderId(folderId);
        documentRepository.save(doc);
        audit(documentId, "MOVE", userId, Map.of("folderId", String.valueOf(folderId)), "SUCCESS");
        return toDto(doc, tagsFor(documentId));
    }

    /**
     * Changes the lifecycle status. Placing a document under legal hold is an administrative action
     * (it blocks deletion); relaxing/archiving it only needs the write capability.
     */
    public DocumentDto setStatus(Long documentId, DocumentStatus status, String reason, Long userId) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        DocumentStatus previous = doc.getStatus();
        if (status == DocumentStatus.LEGAL_HOLD) {
            if (!authz.isAdmin(userId)) {
                throw new AccessDeniedException("Only an administrator may place a document under legal hold");
            }
            doc.setHoldReason(reason);
        } else {
            authz.assertCanWrite(userId, doc);
            doc.setHoldReason(null);
        }
        doc.setStatus(status);
        documentRepository.save(doc);
        audit(documentId, "STATUS_CHANGE", userId,
                Map.of("from", String.valueOf(previous), "to", String.valueOf(status)), "SUCCESS");
        auditEventPublisher.recordChange(userId, "DOCUMENT_STATUS", "DOCUMENT", documentId,
                Map.of("status", String.valueOf(previous)), Map.of("status", String.valueOf(status)));
        return toDto(doc, tagsFor(documentId));
    }

    /**
     * Restores a previous revision by appending a new version that reuses the old revision's stored
     * bytes. Because the bytes already exist there is no additional storage cost, so the quota is not
     * charged again.
     */
    public DocumentVersionDto restoreVersion(Long documentId, Integer versionNo, Long userId) {
        if (versionNo == null) {
            throw new IllegalArgumentException("versionNo is required");
        }
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanWrite(userId, doc);
        DocumentVersion source = versionRepository.findByDocumentIdAndVersionNo(documentId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));

        int next = (doc.getCurrentVersion() == null ? 1 : doc.getCurrentVersion()) + 1;
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(documentId);
        version.setVersionNo(next);
        version.setStorageKey(source.getStorageKey());
        version.setOriginalFilename(source.getOriginalFilename());
        version.setContentType(source.getContentType());
        version.setSizeBytes(source.getSizeBytes());
        version.setSha256(source.getSha256());
        version.setNotes("Restored from version " + versionNo);
        version.setUploadedById(userId);
        version = versionRepository.save(version);

        doc.setCurrentVersion(next);
        doc.setStorageKey(source.getStorageKey());
        doc.setOriginalFilename(source.getOriginalFilename());
        doc.setContentType(source.getContentType());
        doc.setSizeBytes(source.getSizeBytes());
        doc.setSha256(source.getSha256());
        documentRepository.save(doc);

        audit(documentId, "VERSION_RESTORE", userId,
                Map.of("fromVersion", versionNo, "toVersion", next), "SUCCESS");
        return toVersionDto(version);
    }

    /** Live, byte-identical documents the caller is allowed to read (hash de-duplication lookup). */
    @Transactional(readOnly = true)
    public List<DocumentDto> duplicates(Long userId, String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 is required");
        }
        return documentRepository.findBySha256AndIsDeletedFalse(sha256.trim().toLowerCase()).stream()
                .filter(d -> authz.capabilities(userId, d).read())
                .map(d -> toDto(d, tagsFor(d.getId())))
                .toList();
    }

    /** Pre-upload de-duplication check: digests the supplied file and reports readable matches. */
    @Transactional(readOnly = true)
    public DuplicateCheckResult checkDuplicate(MultipartFile file, Long userId) {
        byte[] bytes = readBytes(file);
        String sha = FileCompressionUtil.sha256(bytes);
        return DuplicateCheckResult.builder()
                .sha256(sha)
                .sizeBytes((long) bytes.length)
                .matches(duplicates(userId, sha))
                .build();
    }

    private Optional<DocumentRecord> findReadableDuplicate(Long userId, String sha256) {
        if (sha256 == null) {
            return Optional.empty();
        }
        return documentRepository.findBySha256AndIsDeletedFalse(sha256).stream()
                .filter(d -> authz.capabilities(userId, d).read())
                .findFirst();
    }

    private void assertExternalSharingAllowed(DocumentRecord doc) {
        DocumentClassification c = doc.getClassification();
        if (c == DocumentClassification.RESTRICTED && !properties.getShare().isAllowRestricted()) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN,
                    "External sharing of RESTRICTED documents is disabled by policy");
        }
        if (c == DocumentClassification.CONFIDENTIAL && !properties.getShare().isAllowConfidential()) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN,
                    "External sharing of CONFIDENTIAL documents is disabled by policy");
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    public DocumentPermissionDto grant(Long documentId, GrantPermissionRequest request, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanShare(userId, doc);
        if (request.getSubjectType() == null || request.getSubjectId() == null) {
            throw new IllegalArgumentException("subjectType and subjectId are required");
        }
        DocumentPermission permission = permissionRepository
                .findByDocumentIdAndSubjectTypeAndSubjectId(documentId, request.getSubjectType(), request.getSubjectId())
                .orElseGet(DocumentPermission::new);
        permission.setDocumentId(documentId);
        permission.setSubjectType(request.getSubjectType());
        permission.setSubjectId(request.getSubjectId());
        permission.setCanRead(!Boolean.FALSE.equals(request.getCanRead()));
        permission.setCanWrite(Boolean.TRUE.equals(request.getCanWrite()));
        permission.setCanDelete(Boolean.TRUE.equals(request.getCanDelete()));
        permission.setCanShare(Boolean.TRUE.equals(request.getCanShare()));
        permission.setGrantedById(userId);
        permission = permissionRepository.save(permission);

        audit(documentId, "PERMISSION_GRANT", userId,
                Map.of("subjectType", String.valueOf(request.getSubjectType()), "subjectId", request.getSubjectId()),
                "SUCCESS");
        return toPermissionDto(permission);
    }

    public void revokePermission(Long documentId, Long permissionId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanShare(userId, doc);
        DocumentPermission permission = permissionRepository.findById(permissionId)
                .filter(p -> Objects.equals(p.getDocumentId(), documentId))
                .orElseThrow(() -> new IllegalArgumentException("Permission not found"));
        permissionRepository.delete(permission);
        audit(documentId, "PERMISSION_REVOKE", userId, Map.of("permissionId", permissionId), "SUCCESS");
    }

    // ── Share links ───────────────────────────────────────────────────────────

    public DocumentShareLinkDto createShareLink(Long documentId, CreateShareLinkRequest request, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanShare(userId, doc);
        assertExternalSharingAllowed(doc);

        DocumentShareLink link = new DocumentShareLink();
        link.setDocumentId(documentId);
        link.setToken(newToken());
        link.setExpiresAt(request != null && request.getExpiresInHours() != null
                ? LocalDateTime.now().plusHours(request.getExpiresInHours()) : null);
        link.setMaxDownloads(request == null ? null : request.getMaxDownloads());
        link.setDownloadCount(0);
        link.setRevoked(false);
        link.setCreatedBy(userId);
        if (request != null && request.getPassword() != null && !request.getPassword().isBlank()) {
            link.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        link = shareLinkRepository.save(link);

        audit(documentId, "SHARE_LINK_CREATE", userId, Map.of("linkId", link.getId()), "SUCCESS");
        return toShareLinkDto(link, true);
    }

    public void revokeShareLink(Long documentId, Long linkId, Long userId) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanShare(userId, doc);
        DocumentShareLink link = shareLinkRepository.findById(linkId)
                .filter(l -> Objects.equals(l.getDocumentId(), documentId))
                .orElseThrow(() -> new IllegalArgumentException("Share link not found"));
        link.setRevoked(true);
        shareLinkRepository.save(link);
        audit(documentId, "SHARE_LINK_REVOKE", userId, Map.of("linkId", linkId), "SUCCESS");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private DocumentVersion resolveVersion(DocumentRecord doc, Integer versionNo) {
        if (versionNo != null) {
            return versionRepository.findByDocumentIdAndVersionNo(doc.getId(), versionNo)
                    .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        }
        return versionRepository.findByDocumentIdOrderByVersionNoDesc(doc.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Document has no stored versions"));
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the maximum size of 50MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
    }

    private String store(byte[] bytes, String originalName, Long userId) {
        String key = "documents/" + userId;
        return storageService.uploadRaw(key, bytes, UUID.randomUUID() + "_" + originalName);
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank()) {
            return "document";
        }
        return cleaned.length() > 180 ? cleaned.substring(cleaned.length() - 180) : cleaned;
    }

    private List<Long> nonEmpty(List<Long> ids) {
        return (ids == null || ids.isEmpty()) ? List.of(-1L) : ids;
    }

    private String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private void replaceTags(Long documentId, List<String> tags) {
        tagRepository.deleteByDocumentId(documentId);
        if (tags != null) {
            tags.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .distinct()
                    .limit(50)
                    .forEach(tag -> {
                        DocumentTag entity = new DocumentTag();
                        entity.setDocumentId(documentId);
                        entity.setTag(tag.length() > 80 ? tag.substring(0, 80) : tag);
                        tagRepository.save(entity);
                    });
        }
    }

    private List<String> tagsFor(Long documentId) {
        return tagRepository.findByDocumentId(documentId).stream().map(DocumentTag::getTag).toList();
    }

    private void audit(Long documentId, String action, Long actorId, Object details, String status) {
        try {
            DocumentAuditEvent event = new DocumentAuditEvent();
            event.setDocumentId(documentId);
            event.setAction(action);
            event.setActorId(actorId);
            event.setDetails(details == null ? null : details.toString());
            event.setStatus(status);
            auditRepository.save(event);
        } catch (Exception e) {
            log.warn("Document audit write failed doc#{} action={}: {}", documentId, action, e.getMessage());
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DocumentDto toDto(DocumentRecord d, List<String> tags) {
        return DocumentDto.builder()
                .id(d.getId())
                .title(d.getTitle())
                .originalFilename(d.getOriginalFilename())
                .contentType(d.getContentType())
                .sizeBytes(d.getSizeBytes())
                .sha256(d.getSha256())
                .description(d.getDescription())
                .ownerId(d.getOwnerId())
                .classification(d.getClassification())
                .status(d.getStatus())
                .branchId(d.getBranchId())
                .departmentId(d.getDepartmentId())
                .currentVersion(d.getCurrentVersion())
                .sourceType(d.getSourceType())
                .sourceRefId(d.getSourceRefId())
                .folderId(d.getFolderId())
                .retentionUntil(d.getRetentionUntil())
                .holdReason(d.getHoldReason())
                .tags(tags)
                .createdDate(d.getCreatedDate())
                .modifiedDate(d.getModifiedDate())
                .build();
    }

    private DocumentVersionDto toVersionDto(DocumentVersion v) {
        return DocumentVersionDto.builder()
                .id(v.getId())
                .documentId(v.getDocumentId())
                .versionNo(v.getVersionNo())
                .originalFilename(v.getOriginalFilename())
                .contentType(v.getContentType())
                .sizeBytes(v.getSizeBytes())
                .sha256(v.getSha256())
                .notes(v.getNotes())
                .uploadedById(v.getUploadedById())
                .createdDate(v.getCreatedDate())
                .build();
    }

    private DocumentPermissionDto toPermissionDto(DocumentPermission p) {
        return DocumentPermissionDto.builder()
                .id(p.getId())
                .documentId(p.getDocumentId())
                .subjectType(p.getSubjectType())
                .subjectId(p.getSubjectId())
                .canRead(p.getCanRead())
                .canWrite(p.getCanWrite())
                .canDelete(p.getCanDelete())
                .canShare(p.getCanShare())
                .grantedById(p.getGrantedById())
                .build();
    }

    private DocumentShareLinkDto toShareLinkDto(DocumentShareLink l, boolean includeToken) {
        return DocumentShareLinkDto.builder()
                .id(l.getId())
                .documentId(l.getDocumentId())
                .token(includeToken ? l.getToken() : null)
                .shareUrl(includeToken ? shareBaseUrl.replaceAll("/+$", "") + "/documents/shared/" + l.getToken() : null)
                .expiresAt(l.getExpiresAt())
                .revoked(l.getRevoked())
                .maxDownloads(l.getMaxDownloads())
                .downloadCount(l.getDownloadCount())
                .passwordProtected(l.getPasswordHash() != null)
                .createdById(l.getCreatedBy())
                .createdDate(l.getCreatedDate())
                .build();
    }

    private DocumentAuditEventDto toAuditDto(DocumentAuditEvent e) {
        return DocumentAuditEventDto.builder()
                .id(e.getId())
                .documentId(e.getDocumentId())
                .action(e.getAction())
                .actorId(e.getActorId())
                .details(e.getDetails())
                .status(e.getStatus())
                .createdDate(e.getCreatedDate())
                .build();
    }
}
