package com.nexmemo.core.entity;

import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.Priority;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "memo", indexes = {
    @Index(name = "idx_memo_no", columnList = "memo_no", unique = true),
    @Index(name = "idx_memo_status", columnList = "status"),
    @Index(name = "idx_memo_created_by", columnList = "created_by_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Memo extends BaseEntity<Long> implements Serializable {

    @NotBlank
    @Column(name = "memo_no", unique = true, nullable = false)
    private String memoNo;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoStatus status = MemoStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority = Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memo_type_id")
    private MemoType memoType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_approver_id")
    private User currentApprover;

    @Column(nullable = false)
    private Integer approvalLevel = 0;

    @Column(nullable = false)
    private Integer totalApprovalLevels = 0;

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "memo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("approvalLevel ASC")
    private List<ApprovalHistory> approvalHistory = new ArrayList<>();

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "memo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MemoDocument> documents = new ArrayList<>();

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "memo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MemoCc> ccRecipients = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_category_id")
    private ExpenseCategory expenseCategory;

    @Column(name = "budget_code")
    private String budgetCode;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "vendor_name")
    private String vendorName;

    @Column(name = "vendor_account")
    private String vendorAccount;

    @Column(name = "is_urgent")
    private Boolean isUrgent = false;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id")
    private FiscalYear fiscalYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private MemoCategory category;

    @Column(name = "sla_deadline")
    private LocalDateTime slaDeadline;

    @Column(name = "is_deleted")
    private Boolean isDeleted = false;
}
