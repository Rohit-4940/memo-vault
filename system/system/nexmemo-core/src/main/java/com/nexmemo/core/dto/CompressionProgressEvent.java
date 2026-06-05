package com.nexmemo.core.dto;

import com.nexmemo.core.enums.CompressionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CompressionProgressEvent {
    private Long jobId;
    private String fileName;
    private CompressionStatus status;
    private int progressPercent;
    private String message;
}
