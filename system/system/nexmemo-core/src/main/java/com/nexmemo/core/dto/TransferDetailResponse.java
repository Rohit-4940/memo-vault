package com.nexmemo.core.dto;

import com.nexmemo.core.enums.TransferPriority;
import com.nexmemo.core.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferDetailResponse {
    private Long id;
    private String referenceNo;
    private Long senderId;
    private String senderName;
    private Long senderBranchId;
    private String senderBranchName;
    private Long recipientId;
    private String recipientName;
    private Long recipientBranchId;
    private String recipientBranchName;
    private String subject;
    private String message;
    private TransferStatus transferStatus;
    private TransferPriority priority;
    private int totalFiles;
    private long totalSize;
    private LocalDateTime sentDate;
    private LocalDateTime receivedDate;
    private LocalDateTime archivedDate;
    private LocalDateTime createdDate;
    private String notes;
    private List<TransferFileItem> files;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TransferFileItem {
        private Long id;
        private String originalName;
        private Long fileSize;
        private String mimeType;
    }
}
