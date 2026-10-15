package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "memo_workflow_stage")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class MemoWorkflowStage extends BaseEntity<Long> implements Serializable {

    @Column(name = "config_id")
    private Long configId;

    @Column(name = "stage_order")
    private Integer stageOrder;

    @Column(name = "stage_name")
    private String stageName;

    @Column(name = "assignee_type", length = 20)
    private String assigneeType;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assignee_name")
    private String assigneeName;

    /** Phase 6c — a decision on this stage must include a comment. */
    @Column(name = "require_comment", nullable = false)
    private Boolean requireComment = true;

    /** Phase 6c — approving at this stage requires an attached document. */
    @Column(name = "require_document", nullable = false)
    private Boolean requireDocument = false;
}
