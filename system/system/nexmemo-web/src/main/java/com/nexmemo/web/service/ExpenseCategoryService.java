package com.nexmemo.web.service;

import com.nexmemo.core.entity.ExpenseCategory;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.ExpenseCategoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Transactional(readOnly = true)
    public List<ExpenseCategory> getAllCategories() {
        return expenseCategoryRepository.findByStatus(Status.ACTIVE);
    }

    @Transactional(readOnly = true)
    public ExpenseCategory getCategoryById(Long id) {
        return expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ExpenseCategory not found with id: " + id));
    }

    public ExpenseCategory createCategory(ExpenseCategory category) {
        category.setStatus(category.getStatus() != null ? category.getStatus() : Status.ACTIVE);
        category = expenseCategoryRepository.save(category);
        log.info("ExpenseCategory created: {} ({})", category.getCategoryName(), category.getCategoryCode());
        return category;
    }

    public ExpenseCategory updateCategory(Long id, ExpenseCategory dto) {
        ExpenseCategory existing = expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ExpenseCategory not found with id: " + id));
        existing.setCategoryName(dto.getCategoryName());
        existing.setCategoryCode(dto.getCategoryCode());
        existing.setDescription(dto.getDescription());
        existing.setParentCategory(dto.getParentCategory());
        existing.setBudgetCodePattern(dto.getBudgetCodePattern());
        existing.setMaxAmount(dto.getMaxAmount());
        existing.setRequiresApproval(dto.getRequiresApproval());
        existing.setApprovalLevels(dto.getApprovalLevels());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        existing = expenseCategoryRepository.save(existing);
        log.info("ExpenseCategory updated: {} ({})", existing.getCategoryName(), existing.getCategoryCode());
        return existing;
    }

    public void deleteCategory(Long id) {
        ExpenseCategory category = expenseCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ExpenseCategory not found with id: " + id));
        category.setStatus(Status.DELETED);
        expenseCategoryRepository.save(category);
        log.info("ExpenseCategory soft-deleted: {} ({})", category.getCategoryName(), category.getCategoryCode());
    }
}
