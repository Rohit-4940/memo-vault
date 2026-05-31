package com.nexmemo.web.service;

import com.nexmemo.core.entity.ApprovalRule;
import com.nexmemo.core.entity.Branch;
import com.nexmemo.core.entity.Department;
import com.nexmemo.core.entity.ExpenseCategory;
import com.nexmemo.core.repository.ApprovalRuleRepository;
import com.nexmemo.core.repository.DepartmentRepository;
import com.nexmemo.core.repository.ExpenseCategoryRepository;
import com.nexmemo.security.repository.BranchRepository;
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
public class ApprovalRuleService {

    private final ApprovalRuleRepository approvalRuleRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;

    @Transactional(readOnly = true)
    public List<ApprovalRule> getAllRules() {
        return approvalRuleRepository.findByIsActiveTrueOrderByPriorityOrderAsc();
    }

    @Transactional(readOnly = true)
    public ApprovalRule getRuleById(Long id) {
        return approvalRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApprovalRule not found with id: " + id));
    }

    public ApprovalRule createRule(ApprovalRule rule) {
        resolveForeignKeys(rule);
        ApprovalRule saved = approvalRuleRepository.save(rule);
        log.info("ApprovalRule created: {}", saved.getRuleName());
        return saved;
    }

    public ApprovalRule updateRule(Long id, ApprovalRule dto) {
        ApprovalRule existing = approvalRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApprovalRule not found with id: " + id));
        existing.setRuleName(dto.getRuleName());
        existing.setDescription(dto.getDescription());
        existing.setMinAmount(dto.getMinAmount());
        existing.setMaxAmount(dto.getMaxAmount());
        existing.setApprovalLevelsRequired(dto.getApprovalLevelsRequired());
        existing.setAutoApprove(dto.getAutoApprove());
        existing.setRequiresCommittee(dto.getRequiresCommittee());
        existing.setMinPriority(dto.getMinPriority());
        existing.setPriorityOrder(dto.getPriorityOrder());
        if (dto.getExpenseCategory() != null && dto.getExpenseCategory().getId() != null) {
            ExpenseCategory cat = expenseCategoryRepository.findById(dto.getExpenseCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("ExpenseCategory not found with id: " + dto.getExpenseCategory().getId()));
            existing.setExpenseCategory(cat);
        }
        if (dto.getDepartment() != null && dto.getDepartment().getId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartment().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + dto.getDepartment().getId()));
            existing.setDepartment(dept);
        }
        if (dto.getBranch() != null && dto.getBranch().getId() != null) {
            Branch branch = branchRepository.findById(dto.getBranch().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Branch not found with id: " + dto.getBranch().getId()));
            existing.setBranch(branch);
        }
        existing = approvalRuleRepository.save(existing);
        log.info("ApprovalRule updated: {}", existing.getRuleName());
        return existing;
    }

    public void deleteRule(Long id) {
        ApprovalRule rule = approvalRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApprovalRule not found with id: " + id));
        rule.setIsActive(false);
        approvalRuleRepository.save(rule);
        log.info("ApprovalRule soft-deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRule> getRulesByCategory(Long categoryId) {
        if (!expenseCategoryRepository.existsById(categoryId)) {
            throw new EntityNotFoundException("ExpenseCategory not found with id: " + categoryId);
        }
        return approvalRuleRepository.findByExpenseCategoryIdAndIsActiveTrue(categoryId);
    }

    private void resolveForeignKeys(ApprovalRule rule) {
        if (rule.getExpenseCategory() != null && rule.getExpenseCategory().getId() != null) {
            ExpenseCategory cat = expenseCategoryRepository.findById(rule.getExpenseCategory().getId())
                    .orElseThrow(() -> new EntityNotFoundException("ExpenseCategory not found with id: " + rule.getExpenseCategory().getId()));
            rule.setExpenseCategory(cat);
        }
        if (rule.getDepartment() != null && rule.getDepartment().getId() != null) {
            Department dept = departmentRepository.findById(rule.getDepartment().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + rule.getDepartment().getId()));
            rule.setDepartment(dept);
        }
        if (rule.getBranch() != null && rule.getBranch().getId() != null) {
            Branch branch = branchRepository.findById(rule.getBranch().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Branch not found with id: " + rule.getBranch().getId()));
            rule.setBranch(branch);
        }
    }
}
