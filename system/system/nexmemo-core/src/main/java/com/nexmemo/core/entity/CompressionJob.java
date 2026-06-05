package com.nexmemo.core.entity;

import com.nexmemo.core.enums.CompressionStatus;
import com.nexmemo.core.enums.CompressionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "compression_job", indexes = {
    @Index(name = "idx_cj_status", columnList = "status"),
    @Index(name = "idx_cj_created_by", columnList = "created_by_id"),
    @Index(name = "idx_cj_created_date", columnList = "created_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CompressionJob extends BaseEntity<Long> {

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "original_size", nullable = false)
    private Long originalSize;

    @Column(name = "original_type")
    private String originalType;

    @Column(name = "compressed_name")
    private String compressedName;

    @Column(name = "compressed_size")
    private Long compressedSize;

    @Column(name = "compression_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private CompressionType compressionType;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CompressionStatus status = CompressionStatus.PENDING;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "started_date")
    private LocalDateTime startedDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "checksum_original")
    private String checksumOriginal;

    @Column(name = "checksum_compressed")
    private String checksumCompressed;
}
