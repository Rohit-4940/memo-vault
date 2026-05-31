package com.nexmemo.core.entity;

import com.nexmemo.core.enums.Priority;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "approval_rule", indexes = {
    @Index(name = "idx_ar_name", columnList = "rule_name", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ApprovalRule extends BaseEntity<Long> implements Serializable {

    @NotBlank
    @Column(name = "rule_name", unique = true, nullable = false)
    private String ruleName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "min_amount", precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_category_id")
    private ExpenseCategory expenseCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "approval_levels_required")
    private Integer approvalLevelsRequired = 1;

    @Column(name = "auto_approve")
    private Boolean autoApprove = false;

    @Column(name = "requires_committee")
    private Boolean requiresCommittee = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_priority")
    private Priority minPriority;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "priority_order")
    private Integer priorityOrder;
}
