package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferStats {
    private long totalSent;
    private long totalReceived;
    private long pendingCount;
    private long completedCount;
    private long totalFilesTransferred;
    private long totalBytesTransferred;
}
