package com.nexmemo.core.entity;

import com.nexmemo.core.enums.WorkflowStageStatus;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_stage_instance", indexes = {
    @Index(name = "idx_wsi_memo", columnList = "memo_id"),
    @Index(name = "idx_wsi_assignee", columnList = "assignee_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class WorkflowStageInstance extends BaseEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memo_id", nullable = false)
    private Memo memo;

    /**
     * Global stage reference used by the legacy rule-matrix path. Config-driven (Phase 5) stage
     * instances do not reference a global stage; they snapshot everything on the instance instead,
     * so later config versions can never rewrite an in-progress instance.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private WorkflowStage stage;

    /** Frozen display name for config-driven stages (Phase 5). */
    @Column(name = "stage_name", length = 255)
    private String stageName;

    /** Config id the instance was launched from (Phase 5). */
    @Column(name = "config_id")
    private Long configId;

    /** Config version the instance was launched from — in-flight instances keep this forever (Phase 5). */
    @Column(name = "config_version")
    private Integer configVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /**
     * Frozen assignee pool (comma-separated user ids). Every eligible approver of the stage may
     * act on it, not just the primary {@link #getAssignee()} (Phase 5).
     */
    @Column(name = "assignee_ids", length = 800)
    private String assigneeIds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStageStatus status = WorkflowStageStatus.DRAFT;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "action_taken", length = 50)
    private String actionTaken;

    @Column(name = "action_by")
    private Long actionBy;

    /** Frozen require-comment guard (Phase 5); falls back to the global stage when null. */
    @Column(name = "require_comment")
    private Boolean requireComment;

    /** Frozen require-document guard (Phase 5); falls back to the global stage when null. */
    @Column(name = "require_document")
    private Boolean requireDocument;
}
