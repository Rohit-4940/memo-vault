package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "approval_rule_matrix")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ApprovalRuleMatrix extends BaseEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private MemoCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private WorkflowStage stage;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private Integer priority;

    @Column(name = "can_approve", nullable = false)
    private Boolean canApprove = true;

    @Column(name = "can_reject", nullable = false)
    private Boolean canReject = true;

    @Column(name = "can_return", nullable = false)
    private Boolean canReturn = false;

    @Column(name = "can_forward", nullable = false)
    private Boolean canForward = false;

    @Column(name = "can_delegate", nullable = false)
    private Boolean canDelegate = false;
}
