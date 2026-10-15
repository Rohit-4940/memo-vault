package com.nexmemo.core.repository;

import com.nexmemo.core.entity.FinancialPeriod;
import com.nexmemo.core.enums.FinancialPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, Long>, JpaSpecificationExecutor<FinancialPeriod> {

    Optional<FinancialPeriod> findFirstByStatusOrderByEndDateDesc(FinancialPeriodStatus status);

    List<FinancialPeriod> findByStatus(FinancialPeriodStatus status);

    boolean existsByStatus(FinancialPeriodStatus status);

    List<FinancialPeriod> findByFiscalYearId(Long fiscalYearId);

    Optional<FinancialPeriod> findFirstByFiscalYearIdAndStatus(Long fiscalYearId, FinancialPeriodStatus status);
}