package com.nexmemo.core.dto;

import com.nexmemo.core.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferProgressEvent {
    private Long transferId;
    private String referenceNo;
    private String subject;
    private TransferStatus status;
    private String message;
    private String senderName;
    private String recipientName;
}
