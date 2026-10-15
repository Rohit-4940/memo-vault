package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.CompressionHistoryResponse;
import com.nexmemo.core.dto.CompressionResponse;
import com.nexmemo.core.enums.CompressionPreset;
import com.nexmemo.core.enums.CompressionType;
import com.nexmemo.web.service.CompressionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST surface of the asynchronous compressor.
 *
 * <p>Open to any authenticated user; every operation is scoped to the caller via
 * {@code CompressionService} (jobs are always looked up by {@code id + createdBy}). Submission
 * returns {@code 202 Accepted} with a queued job — the work itself happens on a bounded worker
 * pool, never on this request thread.</p>
 */
@RestController
@RequestMapping("/files/compress")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CompressionController {

    private final CompressionService compressionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompressionResponse>> compress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "GZIP") String type,
            @RequestParam(value = "preset", defaultValue = "BALANCED") String preset,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            CompressionResponse resp = compressionService.submit(
                    file, parseType(type, request), CompressionPreset.from(preset), userId);
            return ResponseEntity.accepted().body(ApiResponse.success(resp));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(ApiResponse.error(e.getMessage(), 503, request.getRequestURI()));
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<List<CompressionResponse>>> compressBatch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "type", defaultValue = "GZIP") String type,
            @RequestParam(value = "preset", defaultValue = "BALANCED") String preset,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("No files provided", 400, request.getRequestURI()));
        }
        try {
            List<CompressionResponse> results = compressionService.submitBatch(
                    files, parseType(type, request), CompressionPreset.from(preset), userId);
            return ResponseEntity.accepted().body(ApiResponse.success(results));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
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

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<ApiResponse<List<CompressionResponse>>> getBatch(
            @PathVariable String batchId,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(compressionService.getBatch(batchId, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompressionResponse>> getJob(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        try {
            return ResponseEntity.ok(ApiResponse.success(compressionService.getJob(id, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        try {
            Resource resource = compressionService.getCompressedResource(id, userId);
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

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<CompressionResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(compressionService.cancel(id, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
        }
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<CompressionResponse>> retry(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(compressionService.retry(id, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
        }
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<ApiResponse<CompressionResponse>> verify(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ApiResponse.success(compressionService.verify(id, userId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
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
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
        }
    }

    private CompressionType parseType(String type, HttpServletRequest request) {
        try {
            return CompressionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid compression type: " + type);
        }
    }
}
