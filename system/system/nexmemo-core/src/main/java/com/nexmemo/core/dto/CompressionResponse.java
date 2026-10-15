package com.nexmemo.core.dto;

import com.nexmemo.core.enums.CompressionPreset;
import com.nexmemo.core.enums.CompressionStatus;
import com.nexmemo.core.enums.CompressionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CompressionResponse {
    private Long id;
    private String originalName;
    private Long originalSize;
    private String originalType;
    private String compressedName;
    private Long compressedSize;
    private CompressionType compressionType;
    private CompressionPreset preset;
    private CompressionStatus status;
    private Integer progressPercent;
    private Integer attempts;
    private String batchId;
    private Boolean verified;
    private Integer archiveEntryCount;
    private Long archiveUncompressedBytes;
    private Long durationMs;
    private int ratioPercent;
    private long savings;
    private int savingsPercent;
    private String errorMessage;
    private LocalDateTime createdDate;
    private LocalDateTime completedDate;
}
