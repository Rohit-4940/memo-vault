package com.nexmemo.core.entity;

import com.nexmemo.core.enums.PermissionSubjectType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Explicit access grant on a {@link DocumentRecord} for a user, role, branch or department.
 *
 * <p>A grant is additive: the owner and administrators always have access, and additional grants
 * widen it. Grants are checked server-side on every access — the client is never trusted to
 * declare what it may read.</p>
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_permission", indexes = {
        @Index(name = "idx_docperm_document", columnList = "document_id"),
        @Index(name = "idx_docperm_subject", columnList = "subject_type,subject_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPermission extends BaseEntity<Long> {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private PermissionSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "can_read", nullable = false)
    private Boolean canRead = true;

    @Column(name = "can_write", nullable = false)
    private Boolean canWrite = false;

    @Column(name = "can_delete", nullable = false)
    private Boolean canDelete = false;

    @Column(name = "can_share", nullable = false)
    private Boolean canShare = false;

    @Column(name = "granted_by_id")
    private Long grantedById;
}
