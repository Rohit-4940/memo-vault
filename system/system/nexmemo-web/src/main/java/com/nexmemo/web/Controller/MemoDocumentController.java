package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoDocument;
import com.nexmemo.web.service.MemoService;
import com.nexmemo.web.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/memo-documents")
@RequiredArgsConstructor
public class MemoDocumentController {

    private final MemoService memoService;
    private final StorageService storageService;

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<MemoDocument>>> getByMemo(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getDocuments(memoId)));
    }

    @PostMapping("/memo/{memoId}/upload")
    public ResponseEntity<ApiResponse<MemoDocument>> uploadDocument(@PathVariable Long memoId,
                                                                    @RequestParam MultipartFile file,
                                                                    @AuthenticationPrincipal Long userId) {
        String dir = "memos/" + memoId;
        String savedPath = storageService.upload(dir, file);

        MemoDocument doc = memoService.addDocument(memoId, file.getOriginalFilename(),
                savedPath, file.getContentType(), file.getSize());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id) {
        memoService.removeDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }
}
