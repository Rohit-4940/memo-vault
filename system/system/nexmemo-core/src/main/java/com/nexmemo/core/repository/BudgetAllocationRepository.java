package com.nexmemo.core.repository;

import com.nexmemo.core.entity.BudgetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, Long> {
    List<BudgetAllocation> findByFiscalYearId(Long fiscalYearId);
    Optional<BudgetAllocation> findByFiscalYearIdAndDepartmentId(Long fiscalYearId, Long departmentId);
}
