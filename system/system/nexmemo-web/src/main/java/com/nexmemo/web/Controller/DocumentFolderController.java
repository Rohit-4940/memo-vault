package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.DocumentFolderDto;
import com.nexmemo.core.dto.SaveFolderRequest;
import com.nexmemo.web.service.DocumentFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vault folder REST API (Enterprise Phase 4).
 *
 * <p>Folders are an owner-scoped organisational overlay; they are never a permission boundary and
 * every operation resolves the caller's own folders server-side. A folder id belonging to another
 * user is treated as not found.</p>
 *
 * @author : NexMemo
 */
@RestController
@RequestMapping("/document-folders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DocumentFolderController {

    private final DocumentFolderService folderService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentFolderDto>>> list(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(folderService.list(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentFolderDto>> create(@RequestBody SaveFolderRequest request,
                                                                 @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(201)
                .body(ApiResponse.created(folderService.create(userId, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentFolderDto>> rename(@PathVariable Long id,
                                                                 @RequestBody SaveFolderRequest request,
                                                                 @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(folderService.rename(userId, id, request)));
    }

    @PatchMapping("/{id}/parent")
    public ResponseEntity<ApiResponse<DocumentFolderDto>> move(@PathVariable Long id,
                                                               @RequestBody SaveFolderRequest request,
                                                               @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                folderService.move(userId, id, request == null ? null : request.getParentId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id,
                                                    @AuthenticationPrincipal Long userId) {
        folderService.delete(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Folder deleted", null));
    }

    @GetMapping("/{id}/breadcrumb")
    public ResponseEntity<ApiResponse<List<DocumentFolderDto>>> breadcrumb(@PathVariable Long id,
                                                                           @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(folderService.breadcrumb(userId, id)));
    }
}
