package com.nexmemo.core.entity;

import com.nexmemo.core.enums.Status;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "budget_allocation", indexes = {
    @Index(name = "idx_budget_fiscal_year", columnList = "fiscal_year_id"),
    @Index(name = "idx_budget_department", columnList = "department_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class BudgetAllocation extends BaseEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", nullable = false)
    private FiscalYear fiscalYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(name = "allocated_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal allocatedAmount;

    @Column(name = "utilized_amount", precision = 19, scale = 4)
    private BigDecimal utilizedAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal remainingAmount;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
}
