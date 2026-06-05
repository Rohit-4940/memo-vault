package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.CompressionHistoryResponse;
import com.nexmemo.core.dto.CompressionResponse;
import com.nexmemo.core.enums.CompressionType;
import com.nexmemo.web.service.CompressionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/files/compress")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class CompressionController {

    private final CompressionService compressionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompressionResponse>> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "GZIP") String type,
            @RequestParam(value = "async", defaultValue = "false") boolean async,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty", 400, request.getRequestURI()));
        }
        try {
            CompressionType ct = CompressionType.valueOf(type.toUpperCase());
            CompressionResponse resp;
            if (async) {
                compressionService.compressAsync(file, ct, userId);
                return ResponseEntity.accepted()
                        .body(ApiResponse.success("Compression started", null));
            } else {
                resp = compressionService.compressSync(file, ct, userId);
                return ResponseEntity.ok(ApiResponse.success(resp));
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid compression type: " + type, 400, request.getRequestURI()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Compression failed: " + e.getMessage(), 500, request.getRequestURI()));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<CompressionResponse>>> compressBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "type", defaultValue = "GZIP") String type,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No files provided", 400, request.getRequestURI()));
        }
        try {
            CompressionType ct = CompressionType.valueOf(type.toUpperCase());
            List<CompressionResponse> results = new ArrayList<>();
            for (MultipartFile file : files) {
                results.add(compressionService.batchCompressSingle(file, ct, userId));
            }
            return ResponseEntity.ok(ApiResponse.success(results));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid compression type: " + type, 400, request.getRequestURI()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<CompressionHistoryResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal Long userId) {
        CompressionHistoryResponse history = compressionService.getHistory(userId, page, size, status, search);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompressionResponse>> getJob(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            CompressionResponse resp = compressionService.getJob(id, userId);
            return ResponseEntity.ok(ApiResponse.success(resp));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        try {
            Path filePath = compressionService.getCompressedFilePath(id, userId);
            Resource resource = new FileSystemResource(filePath.toFile());
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            CompressionResponse job = compressionService.getJob(id, userId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + job.getCompressedName() + "\"")
                    .body(resource);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            compressionService.deleteJob(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Deleted", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
