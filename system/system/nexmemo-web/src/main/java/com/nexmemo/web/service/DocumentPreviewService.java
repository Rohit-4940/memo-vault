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
import com.nexmemo.web.utils.FileKind;
import com.nexmemo.web.utils.FileSignatureValidator;
import com.nexmemo.web.utils.PreviewSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;

/**
 * Safe inline-preview pipeline (Enterprise Phase 4).
 *
 * <p>Previewability is decided from the stored <b>magic bytes</b>, never the declared content type:
 * only raster images and PDFs (plus small, well-formed UTF-8 text) are ever served inline, and they
 * are served with a canonical, non-executable MIME type, {@code Content-Disposition: inline} and a
 * locked-down CSP. HTML, SVG and everything unrecognised stay download-only, so a stored file can
 * never become stored XSS.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentPreviewService {

    private final DocumentAuthorizationService authz;
    private final DocumentRecordRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final StorageService storageService;
    private final ShareLinkGuard shareLinkGuard;
    private final DocumentProperties properties;

    /** A resolved, authorized preview payload. */
    public record Preview(Resource resource, String filename, String contentType, Long sizeBytes, PreviewKind kind) {
    }

    // ── Authenticated preview ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PreviewMetadataDto metadataForUser(Long userId, Long documentId, Integer versionNo) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        return metadataFor(resolveVersion(doc, versionNo));
    }

    @Transactional(readOnly = true)
    public Preview streamForUser(Long userId, Long documentId, Integer versionNo) {
        DocumentRecord doc = authz.requireLiveDocument(userId, documentId);
        authz.assertCanRead(userId, doc);
        return buildPreview(resolveVersion(doc, versionNo));
    }

    // ── Public (token-gated) preview ──────────────────────────────────────────

    /**
     * Landing metadata for a share-link visitor. Rejects revoked/expired links but does <b>not</b>
     * require (or check) the password and does <b>not</b> consume a download.
     */
    @Transactional(readOnly = true)
    public PreviewMetadataDto metadataForShare(String token) {
        DocumentShareLink link = shareLinkGuard.load(token);
        return metadataFor(currentVersion(loadSharedDocument(link)));
    }

    /**
     * Public inline preview of a shared document. Enforces the full link policy (revocation, expiry,
     * cap, password) but, like the landing page, does not consume a download — a preview is a read,
     * the cap governs actual downloads.
     */
    @Transactional(readOnly = true)
    public Preview streamForShare(String token, String password) {
        DocumentShareLink link = shareLinkGuard.validate(token, password);
        return buildPreview(currentVersion(loadSharedDocument(link)));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private PreviewMetadataDto metadataFor(DocumentVersion version) {
        Classified classified = classify(version);
        return PreviewMetadataDto.builder()
                .documentId(version.getDocumentId())
                .versionNo(version.getVersionNo())
                .filename(version.getOriginalFilename())
                .sizeBytes(version.getSizeBytes())
                .previewKind(classified.kind())
                .previewable(classified.kind().isPreviewable())
                .contentType(classified.contentType())
                .build();
    }

    private Preview buildPreview(DocumentVersion version) {
        if (!properties.getPreview().isEnabled()) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN, "Inline preview is disabled");
        }
        Classified classified = classify(version);
        if (!classified.kind().isPreviewable()) {
            throw new DocumentPolicyException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "This file type cannot be previewed inline; download it instead");
        }
        Resource resource = storageService.download(version.getStorageKey());
        return new Preview(resource, version.getOriginalFilename(), classified.contentType(),
                version.getSizeBytes(), classified.kind());
    }

    /** Reads a bounded sample once and derives the safe preview family and canonical MIME type. */
    private Classified classify(DocumentVersion version) {
        long size = version.getSizeBytes() == null ? 0L : version.getSizeBytes();
        long maxText = properties.getPreview().getMaxTextBytes();
        long readLen = size <= maxText ? size : properties.getPreview().getMaxHeadBytes();

        Resource resource = storageService.download(version.getStorageKey());
        byte[] sample = readUpTo(resource, readLen);

        FileKind fileKind = FileSignatureValidator.detect(sample);
        PreviewKind kind = PreviewSupport.previewKindFor(fileKind);
        if (kind == PreviewKind.NONE && size > 0 && size <= maxText && PreviewSupport.isText(sample)) {
            kind = PreviewKind.TEXT;
            fileKind = null;
        }

        String contentType = switch (kind) {
            case IMAGE -> PreviewSupport.mimeFor(fileKind);
            case PDF -> "application/pdf";
            case TEXT -> PreviewSupport.TEXT_MIME;
            case NONE -> null;
        };
        return new Classified(kind, contentType);
    }

    private DocumentVersion resolveVersion(DocumentRecord doc, Integer versionNo) {
        if (versionNo != null) {
            return versionRepository.findByDocumentIdAndVersionNo(doc.getId(), versionNo)
                    .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        }
        return currentVersion(doc);
    }

    private DocumentVersion currentVersion(DocumentRecord doc) {
        return versionRepository.findByDocumentIdOrderByVersionNoDesc(doc.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Document has no stored versions"));
    }

    private DocumentRecord loadSharedDocument(DocumentShareLink link) {
        return documentRepository.findById(link.getDocumentId())
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted()))
                .orElseThrow(() -> new AccessDeniedException("Document is no longer available"));
    }

    /** Reads at most {@code limit} bytes from the resource, closing the stream. */
    private byte[] readUpTo(Resource resource, long limit) {
        if (limit <= 0) {
            return new byte[0];
        }
        int capped = (int) Math.min(limit, Integer.MAX_VALUE);
        try (InputStream in = resource.getInputStream()) {
            return in.readNBytes(capped);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read document content", e);
        }
    }

    private record Classified(PreviewKind kind, String contentType) {
    }
}
