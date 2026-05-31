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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private WorkflowStage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

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
}
