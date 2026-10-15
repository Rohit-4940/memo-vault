package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoDocument;
import com.nexmemo.web.service.MemoAuthorizationService;
import com.nexmemo.web.service.MemoService;
import com.nexmemo.web.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Memo attachment API.
 *
 * <p><b>Security (Phase 2):</b> every endpoint now enforces per-memo authorization through
 * {@link MemoAuthorizationService}. Previously the controller only required authentication, which
 * allowed a user to read, add or delete documents on <em>any</em> memo by id (IDOR/BOLA). Downloads
 * are now served through this authorized id-based endpoint rather than the generic path endpoint.</p>
 *
 * @author : NexMemo
 */
@RestController
@RequestMapping("/memo-documents")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MemoDocumentController {

    private final MemoService memoService;
    private final StorageService storageService;
    private final MemoAuthorizationService memoAuthorizationService;

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<MemoDocument>>> getByMemo(@PathVariable Long memoId,
                                                                    @AuthenticationPrincipal Long userId) {
        memoAuthorizationService.assertCanViewMemo(userId, memoId);
        return ResponseEntity.ok(ApiResponse.success(memoService.getDocuments(memoId)));
    }

    /** Authorized, id-based download. Replaces reliance on the generic {@code /files/download}. */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id,
                                                     @AuthenticationPrincipal Long userId) {
        Long memoId = memoService.getMemoIdForDocument(id);
        memoAuthorizationService.assertCanViewMemo(userId, memoId);
        MemoDocument doc = memoService.getDocument(id);
        Resource resource = storageService.download(doc.getFilePath());
        String filename = doc.getFileName() == null ? "document"
                : doc.getFileName().replace("\"", "");
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (doc.getFileType() != null) {
            try {
                type = MediaType.parseMediaType(doc.getFileType());
            } catch (Exception ignored) {
                // keep octet-stream
            }
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }

    @PostMapping("/memo/{memoId}/upload")
    public ResponseEntity<ApiResponse<MemoDocument>> uploadDocument(@PathVariable Long memoId,
                                                                    @RequestParam MultipartFile file,
                                                                    @AuthenticationPrincipal Long userId) {
        memoAuthorizationService.assertCanAttachDocument(userId, memoId);
        String dir = "memos/" + memoId;
        String savedPath = storageService.uploadRaw(dir, readBytes(file), safeName(file.getOriginalFilename()));

        MemoDocument doc = memoService.addDocument(memoId, file.getOriginalFilename(),
                savedPath, file.getContentType(), file.getSize());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id,
                                                            @AuthenticationPrincipal Long userId) {
        Long memoId = memoService.getMemoIdForDocument(id);
        memoAuthorizationService.assertCanEditMemo(userId, memoId);
        memoService.removeDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "document";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.isBlank() ? "document" : cleaned;
    }
}
