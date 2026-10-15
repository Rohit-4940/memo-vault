package com.nexmemo.core.entity;

import com.nexmemo.core.enums.CompressionPreset;
import com.nexmemo.core.enums.CompressionStatus;
import com.nexmemo.core.enums.CompressionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A single asynchronous compression job.
 *
 * <p>Phase 3 additions: {@code progressPercent}, {@code cancelRequested}, {@code attempts},
 * {@code preset}, {@code batchId} and the split input/output storage keys. Bytes are held in
 * private storage under a generated key — never referenced by an absolute path and never by a
 * value the client can guess.</p>
 */
@Entity
@Table(name = "compression_job", indexes = {
    @Index(name = "idx_cj_status", columnList = "status"),
    @Index(name = "idx_cj_created_by", columnList = "created_by_id"),
    @Index(name = "idx_cj_created_date", columnList = "created_date"),
    @Index(name = "idx_cj_batch", columnList = "batch_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CompressionJob extends BaseEntity<Long> {

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "original_size", nullable = false)
    private Long originalSize;

    @Column(name = "original_type", length = 100)
    private String originalType;

    @Column(name = "compressed_name", length = 500)
    private String compressedName;

    @Column(name = "compressed_size")
    private Long compressedSize;

    @Column(name = "compression_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CompressionType compressionType;

    @Column(name = "preset", length = 30)
    @Enumerated(EnumType.STRING)
    private CompressionPreset preset;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CompressionStatus status = CompressionStatus.QUEUED;

    /** 0-100, advanced by the worker; the UI renders this as a progress bar. */
    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    /** Set by the owner; the worker checks it between stages and stops cleanly. */
    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested = false;

    /** How many times this job has been attempted (submission + retries). */
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    /** Groups jobs submitted together as a batch, so the UI can present them as one operation. */
    @Column(name = "batch_id", length = 64)
    private String batchId;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** Private storage key of the original upload (input to the worker). */
    @Column(name = "input_storage_key", length = 500)
    private String inputStorageKey;

    /** Private storage key of the produced artefact. */
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    /** Legacy absolute path from before Phase 3; read-only fallback for old rows. */
    @Column(name = "storage_path", length = 1000)
    private String storagePath;

    @Column(name = "started_date")
    private LocalDateTime startedDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "checksum_original", length = 64)
    private String checksumOriginal;

    @Column(name = "checksum_compressed", length = 64)
    private String checksumCompressed;

    /** Result of the last integrity verification, if one was requested. */
    @Column(name = "verified")
    private Boolean verified;

    /** Guardrail summary captured while inspecting an archive input. */
    @Column(name = "archive_entry_count")
    private Integer archiveEntryCount;

    @Column(name = "archive_uncompressed_bytes")
    private Long archiveUncompressedBytes;
}
