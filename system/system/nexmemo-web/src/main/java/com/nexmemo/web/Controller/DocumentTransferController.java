package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.*;
import com.nexmemo.web.service.DocumentTransferService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/files/transfer")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DocumentTransferController {

    private final DocumentTransferService transferService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> createTransfer(
            @RequestParam("recipientIds") String recipientIds,
            @RequestParam("recipientNames") String recipientNames,
            @RequestParam(value = "recipientBranchIds", required = false) String recipientBranchIds,
            @RequestParam(value = "recipientBranchNames", required = false) String recipientBranchNames,
            @RequestParam("subject") String subject,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "priority", defaultValue = "MEDIUM") String priority,
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("At least one file is required", 400, request.getRequestURI()));
        }

        TransferRequest transferRequest = TransferRequest.builder()
                .recipientId(parseFirstId(recipientIds))
                .recipientName(recipientNames)
                .recipientBranchId(null)
                .recipientBranchName(recipientBranchNames)
                .subject(subject)
                .message(message)
                .priority(priority)
                .build();

        TransferResponse resp = transferService.createTransfer(transferRequest, files, userId);
        return ResponseEntity.ok(ApiResponse.success("Transfer created", resp));
    }

    private Long parseFirstId(String ids) {
        if (ids == null || ids.isBlank()) return 0L;
        try {
            return Long.parseLong(ids.split(",")[0].trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<ApiResponse<TransferResponse>> sendTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        TransferResponse resp = transferService.sendTransfer(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Transfer sent", resp));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<TransferHistoryResponse>> getSentTransfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal Long userId) {
        TransferHistoryResponse history = transferService.getSentTransfers(userId, page, size, status, search);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<TransferHistoryResponse>> getReceivedTransfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal Long userId) {
        TransferHistoryResponse history = transferService.getReceivedTransfers(userId, page, size, status, search);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferDetailResponse>> getTransferDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            TransferDetailResponse detail = transferService.getTransferDetail(id, userId);
            return ResponseEntity.ok(ApiResponse.success(detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<ApiResponse<TransferResponse>> receiveTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        TransferResponse resp = transferService.markAsReceived(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Transfer received", resp));
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<TransferResponse>> deliverTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        TransferResponse resp = transferService.markAsDelivered(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Transfer marked as delivered", resp));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<TransferResponse>> archiveTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        TransferResponse resp = transferService.archiveTransfer(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Transfer archived", resp));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelTransfer(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        try {
            transferService.cancelTransfer(id, userId);
            return ResponseEntity.ok(ApiResponse.success("Transfer cancelled", null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage(), 400, request.getRequestURI()));
        }
    }

    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @PathVariable Long fileId,
            @AuthenticationPrincipal Long userId) {
        try {
            Path filePath = transferService.getTransferFilePath(fileId, userId);
            Resource resource = new ByteArrayResource(Files.readAllBytes(filePath));
            String fileName = transferService.getTransferFileName(fileId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/download-all")
    public ResponseEntity<Resource> downloadAll(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        try {
            byte[] zipData = transferService.downloadAllAsZip(id, userId);
            Resource resource = new ByteArrayResource(zipData);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"transfer-" + id + "-files.zip\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<TransferStats>> getStats(
            @AuthenticationPrincipal Long userId) {
        TransferStats stats = transferService.getStats(userId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
