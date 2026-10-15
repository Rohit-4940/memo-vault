package com.nexmemo.core.entity;

import com.nexmemo.core.enums.FinancialPeriodStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * A financial period (e.g. a quarter or phase) belonging to a {@link FiscalYear}. Periods carry an
 * open / closed / locked lifecycle so the business can control when new memos may be created
 * (Phase 4b).
 *
 * <p>At most one period may be {@code OPEN} at any time; memo creation is gated on an open period
 * once periods are configured.</p>
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "financial_period", indexes = {
    @Index(name = "idx_financial_period_fy_status", columnList = "fiscal_year_id, status"),
    @Index(name = "idx_financial_period_dates", columnList = "start_date, end_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class FinancialPeriod extends BaseEntity<Long> implements Serializable {

    @NotBlank(message = "Financial period name is required")
    @Column(nullable = false)
    private String name;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", nullable = false)
    private FiscalYear fiscalYear;

    @NotNull
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @NotNull
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FinancialPeriodStatus status = FinancialPeriodStatus.CLOSED;
}