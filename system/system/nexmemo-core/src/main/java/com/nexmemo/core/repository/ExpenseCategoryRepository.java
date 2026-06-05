package com.nexmemo.core.repository;

import com.nexmemo.core.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long>, JpaSpecificationExecutor<ExpenseCategory> {
    Optional<ExpenseCategory> findByCategoryCode(String categoryCode);
    List<ExpenseCategory> findByParentCategoryIsNull();
    List<ExpenseCategory> findByParentCategoryId(Long parentId);
    List<ExpenseCategory> findByStatus(com.nexmemo.core.enums.Status status);
}
