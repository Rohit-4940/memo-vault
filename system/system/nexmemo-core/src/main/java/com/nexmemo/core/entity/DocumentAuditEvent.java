package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dedicated audit trail for document events (upload, download, share, permission change,
 * delete, denied access). Kept beside the generic {@code AuditLog} so document history is
 * queryable per document; both are written for sensitive operations.
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_audit_event", indexes = {
        @Index(name = "idx_docaudit_document", columnList = "document_id,created_date"),
        @Index(name = "idx_docaudit_actor", columnList = "actor_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAuditEvent extends BaseEntity<Long> {

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUCCESS";
}
