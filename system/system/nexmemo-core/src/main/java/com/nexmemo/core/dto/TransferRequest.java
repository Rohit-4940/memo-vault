package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferRequest {
    private Long recipientId;
    private String recipientName;
    private Long recipientBranchId;
    private String recipientBranchName;
    private String subject;
    private String message;
    private String priority;
}
