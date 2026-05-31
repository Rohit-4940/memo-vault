package com.nexmemo.core.entity;

import com.nexmemo.core.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "expense_category", indexes = {
    @Index(name = "idx_exp_cat_code", columnList = "category_code", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ExpenseCategory extends BaseEntity<Long> implements Serializable {

    @NotBlank
    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @NotBlank
    @Column(name = "category_code", unique = true, nullable = false)
    private String categoryCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private ExpenseCategory parentCategory;

    @Column(name = "budget_code_pattern")
    private String budgetCodePattern;

    @Column(name = "max_amount", precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "requires_approval")
    private Boolean requiresApproval = true;

    @Column(name = "approval_levels")
    private Integer approvalLevels = 1;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
}
