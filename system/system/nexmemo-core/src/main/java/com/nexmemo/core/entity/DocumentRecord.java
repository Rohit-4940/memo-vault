package com.nexmemo.core.entity;

import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentSourceType;
import com.nexmemo.core.enums.DocumentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Canonical document metadata record (Phase 2 — document foundation).
 *
 * <p>Bytes live in private object/local storage ({@code storage_key} is a generated identifier,
 * never a user-supplied filename). SQL Server holds only metadata + controlled references, so a
 * document can be referenced from a memo, a chat message and the vault without uncontrolled
 * copies.</p>
 *
 * <p>Access is <b>never</b> implied by knowing the id: every read/write resolves the caller's
 * permission through the authorization service.</p>
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_record", indexes = {
        @Index(name = "idx_docrec_owner", columnList = "owner_id"),
        @Index(name = "idx_docrec_status", columnList = "status"),
        @Index(name = "idx_docrec_sha256", columnList = "sha256"),
        @Index(name = "idx_docrec_source", columnList = "source_type,source_ref_id"),
        @Index(name = "idx_docrec_created", columnList = "created_date"),
        @Index(name = "idx_docrec_folder", columnList = "folder_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord extends BaseEntity<Long> {

    /** Generated storage identifier (relative path within the storage backend). */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 30, nullable = false)
    private DocumentClassification classification = DocumentClassification.INTERNAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "department_id")
    private Long departmentId;

    /** Number of the latest {@link DocumentVersion}. */
    @Column(name = "current_version", nullable = false)
    private Integer currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20, nullable = false)
    private DocumentSourceType sourceType = DocumentSourceType.UPLOAD;

    /** Optional id in the originating domain (memo id, chat conversation id, transfer id, …). */
    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "retention_until")
    private LocalDateTime retentionUntil;

    /** Organisational folder (Phase 4); null = uncategorised. Never affects authorization. */
    @Column(name = "folder_id")
    private Long folderId;

    /** Explanation captured when the document was placed under legal hold (Phase 4). */
    @Column(name = "hold_reason", length = 500)
    private String holdReason;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
