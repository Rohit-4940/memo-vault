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
}
