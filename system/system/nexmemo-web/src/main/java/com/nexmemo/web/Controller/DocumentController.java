package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.*;
import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentStatus;
import com.nexmemo.web.service.DocumentPreviewService;
import com.nexmemo.web.service.DocumentQuotaService;
import com.nexmemo.web.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Document vault REST API (Phase 2 foundation + Enterprise Phase 4 secure vault).
 *
 * <p>Every authenticated endpoint is authorized server-side through
 * {@link com.nexmemo.web.service.DocumentAuthorizationService}; a caller who merely knows a document
 * id can never read it. The only unauthenticated routes are the token-gated {@code /documents/shared}
 * endpoints, where the opaque token is the credential and expiry/revocation/caps/password are
 * re-checked on every access.</p>
 *
 * <p>Inline previews are served only for magic-byte-verified images/PDF/text and always carry
 * {@code nosniff}, a locked-down CSP and {@code Content-Disposition: inline}; everything else is
 * download-only, so stored content can never become stored XSS.</p>
 *
 * @author : NexMemo
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentPreviewService previewService;
    private final DocumentQuotaService quotaService;

    // ── Upload / list / read ──────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentDto>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "classification", required = false) String classification,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "departmentId", required = false) Long departmentId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "reuseExisting", defaultValue = "false") boolean reuseExisting,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @AuthenticationPrincipal Long userId) {
        DocumentDto dto = documentService.upload(file, title, description,
                parseClassification(classification), branchId, departmentId, tags, folderId, reuseExisting, userId);
        return ResponseEntity.status(201).body(ApiResponse.created(dto));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DocumentDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String classification,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tag,
            @AuthenticationPrincipal Long userId) {
        Page<DocumentDto> result = documentService.list(userId, search, folderId,
                parseClassification(classification), parseStatus(status), tag,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdDate")));
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentDto>> get(@PathVariable Long id,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.get(id, userId)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentDto>> updateMetadata(
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "classification", required = false) String classification,
            @RequestParam(value = "retentionUntil", required = false) String retentionUntil,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @AuthenticationPrincipal Long userId) {
        DocumentDto dto = documentService.updateMetadata(id, title, description,
                parseClassification(classification), tags, parseDateTime(retentionUntil), userId);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id,
                                                    @RequestParam(value = "force", defaultValue = "false") boolean force,
                                                    @AuthenticationPrincipal Long userId) {
        documentService.delete(id, userId, force);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    // ── Catalogue organisation & lifecycle ────────────────────────────────────

    @PatchMapping("/{id}/folder")
    public ResponseEntity<ApiResponse<DocumentDto>> move(@PathVariable Long id,
                                                         @RequestBody MoveDocumentRequest request,
                                                         @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.moveDocument(id, request == null ? null : request.getFolderId(), userId)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DocumentDto>> setStatus(@PathVariable Long id,
                                                              @RequestBody DocumentStatusRequest request,
                                                              @AuthenticationPrincipal Long userId) {
        if (request == null) {
            throw new IllegalArgumentException("status is required");
        }
        return ResponseEntity.ok(ApiResponse.success(
                documentService.setStatus(id, request.getStatus(), request.getReason(), userId)));
    }

    // ── Versions ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<DocumentVersionDto>>> versions(
            @PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.versions(id, userId)));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<DocumentVersionDto>> addVersion(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "notes", required = false) String notes,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(documentService.addVersion(id, file, notes, userId)));
    }

    @PostMapping("/{id}/versions/{versionNo}/restore")
    public ResponseEntity<ApiResponse<DocumentVersionDto>> restoreVersion(
            @PathVariable Long id, @PathVariable Integer versionNo,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(documentService.restoreVersion(id, versionNo, userId)));
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}/preview-metadata")
    public ResponseEntity<ApiResponse<PreviewMetadataDto>> previewMetadata(
            @PathVariable Long id,
            @RequestParam(value = "version", required = false) Integer version,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(previewService.metadataForUser(userId, id, version)));
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id,
                                            @RequestParam(value = "version", required = false) Integer version,
                                            @AuthenticationPrincipal Long userId) {
        return previewResponse(previewService.streamForUser(userId, id, version));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam(value = "version", required = false) Integer version,
                                             @AuthenticationPrincipal Long userId) {
        return fileResponse(documentService.download(id, userId, version));
    }

    // ── De-duplication & quota ────────────────────────────────────────────────

    @GetMapping("/duplicates")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> duplicates(
            @RequestParam("sha256") String sha256, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.duplicates(userId, sha256)));
    }

    @PostMapping("/duplicates/check")
    public ResponseEntity<ApiResponse<DuplicateCheckResult>> checkDuplicate(
            @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.checkDuplicate(file, userId)));
    }

    @GetMapping("/quota")
    public ResponseEntity<ApiResponse<DocumentQuotaDto>> quota(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(quotaService.usage(userId)));
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    @GetMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<List<DocumentPermissionDto>>> permissions(
            @PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.permissions(id, userId)));
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<ApiResponse<DocumentPermissionDto>> grant(
            @PathVariable Long id,
            @RequestBody GrantPermissionRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.grant(id, request, userId)));
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ResponseEntity<ApiResponse<Void>> revokePermission(
            @PathVariable Long id, @PathVariable Long permissionId,
            @AuthenticationPrincipal Long userId) {
        documentService.revokePermission(id, permissionId, userId);
        return ResponseEntity.ok(ApiResponse.success("Permission revoked", null));
    }

    // ── Share links (owner-facing) ────────────────────────────────────────────

    @GetMapping("/{id}/share-links")
    public ResponseEntity<ApiResponse<List<DocumentShareLinkDto>>> shareLinks(
            @PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.shareLinks(id, userId)));
    }

    @PostMapping("/{id}/share-links")
    public ResponseEntity<ApiResponse<DocumentShareLinkDto>> createShareLink(
            @PathVariable Long id,
            @RequestBody(required = false) CreateShareLinkRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(documentService.createShareLink(id, request, userId)));
    }

    @DeleteMapping("/{id}/share-links/{linkId}")
    public ResponseEntity<ApiResponse<Void>> revokeShareLink(
            @PathVariable Long id, @PathVariable Long linkId,
            @AuthenticationPrincipal Long userId) {
        documentService.revokeShareLink(id, linkId, userId);
        return ResponseEntity.ok(ApiResponse.success("Share link revoked", null));
    }

    // ── Public share-link access ──────────────────────────────────────────────

    /** Landing metadata for a share link: filename, size, password requirement, expiry, remaining. */
    @GetMapping("/shared/{token}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<ShareLinkLandingDto>> sharedLanding(@PathVariable String token) {
        ShareLinkLandingDto landing = documentService.shareLanding(token);
        PreviewMetadataDto preview = previewService.metadataForShare(token);
        landing.setPreviewKind(preview.getPreviewKind());
        landing.setPreviewable(preview.isPreviewable());
        landing.setContentType(preview.getContentType());
        return ResponseEntity.ok(ApiResponse.success(landing));
    }

    /**
     * Public, token-gated redemption of a share link. The password travels in the request body so it
     * never lands in access logs, browser history or a {@code Referer} header. (The legacy GET form is
     * retained below for compatibility but should be treated as deprecated.)
     */
    @PostMapping("/shared/{token}/download")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Resource> sharedDownloadPost(@PathVariable String token,
                                                       @RequestBody(required = false) RedeemShareLinkRequest request) {
        String password = request == null ? null : request.getPassword();
        return fileResponse(documentService.redeemShareLink(token, password));
    }

    /** @deprecated password-in-query leaks into logs/history; prefer {@link #sharedDownloadPost}. */
    @Deprecated
    @GetMapping("/shared/{token}/download")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Resource> sharedDownload(@PathVariable String token,
                                                   @RequestParam(value = "password", required = false) String password) {
        return fileResponse(documentService.redeemShareLink(token, password));
    }

    /** Public inline preview of a shared document (same link policy; does not consume a download). */
    @GetMapping("/shared/{token}/preview")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Resource> sharedPreview(@PathVariable String token,
                                                  @RequestParam(value = "password", required = false) String password) {
        return previewResponse(previewService.streamForShare(token, password));
    }

    // ── Audit ─────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/audit")
    public ResponseEntity<ApiResponse<List<DocumentAuditEventDto>>> audit(
            @PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(documentService.auditTrail(id, userId)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DocumentClassification parseClassification(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DocumentClassification.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown classification: " + value);
        }
    }

    private DocumentStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }

    /** Accepts an ISO date-time ({@code 2027-01-31T00:00}) or an ISO date ({@code 2027-01-31}). */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return v.length() == 10 ? LocalDate.parse(v).atStartOfDay() : LocalDateTime.parse(v);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date: " + value);
        }
    }

    private ResponseEntity<Resource> fileResponse(DocumentService.Download d) {
        String filename = d.filename() == null ? "document" : d.filename().replace("\"", "");
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (d.contentType() != null) {
            try {
                type = MediaType.parseMediaType(d.contentType());
            } catch (Exception ignored) {
                // fall back to octet-stream
            }
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        if (d.sizeBytes() != null) {
            builder.contentLength(d.sizeBytes());
        }
        return builder.body(d.resource());
    }

    /** Streams a verified-inline preview with hardened, per-response headers. */
    private ResponseEntity<Resource> previewResponse(DocumentPreviewService.Preview p) {
        String filename = p.filename() == null ? "document" : p.filename().replace("\"", "");
        MediaType type;
        try {
            type = MediaType.parseMediaType(p.contentType());
        } catch (Exception e) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                // Defence-in-depth for the unauthenticated share-preview path: the bytes may render,
                // but nothing they contain may load scripts or external resources. `sandbox` is
                // deliberately omitted so the browser's built-in PDF viewer keeps working.
                .header("Content-Security-Policy",
                        "default-src 'none'; img-src 'self' data: blob:; style-src 'unsafe-inline'; object-src 'none'; base-uri 'none'")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (p.sizeBytes() != null) {
            builder.contentLength(p.sizeBytes());
        }
        return builder.body(p.resource());
    }
}
