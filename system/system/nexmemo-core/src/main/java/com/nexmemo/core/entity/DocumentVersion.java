package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable snapshot of one revision of a {@link DocumentRecord}. Every upload of a new revision
 * appends a row; the parent's {@code current_version} points at the newest. Storing each revision
 * separately is what makes version integrity and version-to-version comparison possible.
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_version", indexes = {
        @Index(name = "idx_docver_document", columnList = "document_id,version_no"),
        @Index(name = "idx_docver_sha256", columnList = "sha256")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersion extends BaseEntity<Long> {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

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

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "uploaded_by_id", nullable = false)
    private Long uploadedById;
}
