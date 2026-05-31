package com.nexmemo.core.entity;

import com.nexmemo.core.enums.EscalationAction;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "escalation_rule")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class EscalationRule extends BaseEntity<Long> implements Serializable {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private MemoCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id")
    private WorkflowStage stage;

    @Column(name = "timeout_hours", nullable = false)
    private Integer timeoutHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_action", nullable = false)
    private EscalationAction escalationAction;

    @Column(name = "escalate_to_role")
    private Long escalateToRole;

    @Column(name = "escalate_to_user")
    private Long escalateToUser;

    @Column(name = "is_global", nullable = false)
    private Boolean isGlobal = false;
}
