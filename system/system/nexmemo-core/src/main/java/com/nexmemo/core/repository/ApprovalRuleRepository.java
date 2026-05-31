package com.nexmemo.core.repository;

import com.nexmemo.core.entity.ApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, Long> {
    List<ApprovalRule> findByExpenseCategoryId(Long categoryId);
    List<ApprovalRule> findByDepartmentId(Long departmentId);
    List<ApprovalRule> findByIsActiveTrueOrderByPriorityOrderAsc();
    List<ApprovalRule> findByExpenseCategoryIdAndIsActiveTrue(Long categoryId);
}
