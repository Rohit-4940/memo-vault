package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "memo_workflow_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class MemoWorkflowConfig extends BaseEntity<Long> implements Serializable {

    @Column(name = "memo_type_id")
    private Long memoTypeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private MemoCategory category;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "require_document_verification", nullable = false)
    private Boolean requireDocumentVerification = false;

    @Column(name = "enable_versioning", nullable = false)
    private Boolean enableVersioning = true;

    @Column(name = "assignment_type", length = 50)
    private String assignmentType = "USER_BASED";

    @Column(name = "flow_type", length = 50)
    private String flowType = "SEQUENTIAL";

    /**
     * Config revision. Bumped whenever the config or its stage set changes (Phase 5). In-flight
     * workflow instances store the revision they were launched with, and are never rewritten by
     * newer revisions. (The BaseEntity {@code version} column stays reserved for optimistic
     * locking, so a separate {@code revision} column is used here.)
     */
    @Column(nullable = false)
    private Integer revision = 1;

    @Column(name = "required_fields", columnDefinition = "TEXT")
    private String requiredFields;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
