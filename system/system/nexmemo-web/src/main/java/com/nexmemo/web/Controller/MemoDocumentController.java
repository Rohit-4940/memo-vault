package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoDocument;
import com.nexmemo.web.service.MemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@RestController
@RequestMapping("/memo-documents")
@RequiredArgsConstructor
public class MemoDocumentController {

    private final MemoService memoService;

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<MemoDocument>>> getByMemo(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getDocuments(memoId)));
    }

    @PostMapping("/memo/{memoId}/upload")
    public ResponseEntity<ApiResponse<MemoDocument>> uploadDocument(@PathVariable Long memoId,
                                                                    @RequestParam MultipartFile file,
                                                                    @AuthenticationPrincipal Long userId) {
        try {
            String uploadDir = "uploads/memos/" + memoId + "/";
            Path dirPath = Paths.get(uploadDir);
            Files.createDirectories(dirPath);

            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path filePath = dirPath.resolve(fileName);
            Files.write(filePath, file.getBytes());

            MemoDocument doc = memoService.addDocument(memoId, file.getOriginalFilename(),
                    filePath.toString(), file.getContentType(), file.getSize());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.created(doc));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to upload file: " + e.getMessage(), 500));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable Long id) {
        memoService.removeDocument(id);
        return ResponseEntity.ok(ApiResponse.success("Document deleted", null));
    }
}
