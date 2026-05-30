package com.nexmemo.web.service;

import com.nexmemo.core.entity.BudgetAllocation;
import com.nexmemo.core.entity.Department;
import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.BudgetAllocationRepository;
import com.nexmemo.core.repository.DepartmentRepository;
import com.nexmemo.core.repository.FiscalYearRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BudgetAllocationService {

    private final BudgetAllocationRepository budgetAllocationRepository;
    private final FiscalYearRepository fiscalYearRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<BudgetAllocation> getAll() {
        return budgetAllocationRepository.findAll();
    }

    @Transactional(readOnly = true)
    public BudgetAllocation getById(Long id) {
        return budgetAllocationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BudgetAllocation not found with id: " + id));
    }

    public BudgetAllocation create(BudgetAllocation ba) {
        Long fyId = ba.getFiscalYear() != null ? ba.getFiscalYear().getId() : null;
        if (fyId != null) {
            FiscalYear fy = fiscalYearRepository.findById(fyId)
                    .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + fyId));
            ba.setFiscalYear(fy);
        }
        Long deptId = ba.getDepartment() != null ? ba.getDepartment().getId() : null;
        if (deptId != null) {
            Department dept = departmentRepository.findById(deptId)
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + deptId));
            ba.setDepartment(dept);
        }
        ba.setUtilizedAmount(BigDecimal.ZERO);
        ba.setRemainingAmount(ba.getAllocatedAmount());
        ba.setStatus(ba.getStatus() != null ? ba.getStatus() : Status.ACTIVE);
        BudgetAllocation saved = budgetAllocationRepository.save(ba);
        log.info("BudgetAllocation created: {} for fiscal year {}", saved.getId(), saved.getFiscalYear().getName());
        return saved;
    }

    public BudgetAllocation update(Long id, BudgetAllocation ba) {
        BudgetAllocation existing = budgetAllocationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BudgetAllocation not found with id: " + id));
        if (ba.getFiscalYear() != null && ba.getFiscalYear().getId() != null) {
            FiscalYear fy = fiscalYearRepository.findById(ba.getFiscalYear().getId())
                    .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + ba.getFiscalYear().getId()));
            existing.setFiscalYear(fy);
        }
        if (ba.getDepartment() != null && ba.getDepartment().getId() != null) {
            Department dept = departmentRepository.findById(ba.getDepartment().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + ba.getDepartment().getId()));
            existing.setDepartment(dept);
        }
        if (ba.getAllocatedAmount() != null) {
            existing.setAllocatedAmount(ba.getAllocatedAmount());
            existing.setRemainingAmount(ba.getAllocatedAmount().subtract(existing.getUtilizedAmount()));
        }
        existing.setDescription(ba.getDescription());
        if (ba.getStatus() != null) existing.setStatus(ba.getStatus());
        existing = budgetAllocationRepository.save(existing);
        log.info("BudgetAllocation updated: {}", existing.getId());
        return existing;
    }

    public void delete(Long id) {
        if (!budgetAllocationRepository.existsById(id)) {
            throw new EntityNotFoundException("BudgetAllocation not found with id: " + id);
        }
        budgetAllocationRepository.deleteById(id);
        log.info("BudgetAllocation deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public List<BudgetAllocation> getByFiscalYear(Long fiscalYearId) {
        if (!fiscalYearRepository.existsById(fiscalYearId)) {
            throw new EntityNotFoundException("FiscalYear not found with id: " + fiscalYearId);
        }
        return budgetAllocationRepository.findByFiscalYearId(fiscalYearId);
    }

    @Transactional(readOnly = true)
    public BudgetAllocation getByDepartmentAndFiscalYear(Long deptId, Long fiscalYearId) {
        return budgetAllocationRepository.findByFiscalYearIdAndDepartmentId(fiscalYearId, deptId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "BudgetAllocation not found for department " + deptId + " and fiscal year " + fiscalYearId));
    }

    public BudgetAllocation allocateBudget(Long fiscalYearId, Long deptId, BigDecimal amount, String description) {
        BudgetAllocation ba = budgetAllocationRepository.findByFiscalYearIdAndDepartmentId(fiscalYearId, deptId)
                .orElseGet(() -> {
                    BudgetAllocation newBa = new BudgetAllocation();
                    newBa.setFiscalYear(fiscalYearRepository.findById(fiscalYearId)
                            .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + fiscalYearId)));
                    newBa.setDepartment(departmentRepository.findById(deptId)
                            .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + deptId)));
                    newBa.setUtilizedAmount(BigDecimal.ZERO);
                    newBa.setStatus(Status.ACTIVE);
                    return newBa;
                });
        ba.setAllocatedAmount(amount);
        ba.setRemainingAmount(amount.subtract(ba.getUtilizedAmount()));
        ba.setDescription(description);
        ba = budgetAllocationRepository.save(ba);
        log.info("Budget allocated: {} for department {} fiscal year {}", amount, deptId, fiscalYearId);
        return ba;
    }

    public BudgetAllocation utilizeBudget(Long id, BigDecimal amount) {
        BudgetAllocation ba = budgetAllocationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("BudgetAllocation not found with id: " + id));
        if (ba.getRemainingAmount().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient remaining budget. Available: " + ba.getRemainingAmount()
                    + ", Requested: " + amount);
        }
        ba.setUtilizedAmount(ba.getUtilizedAmount().add(amount));
        ba.setRemainingAmount(ba.getRemainingAmount().subtract(amount));
        ba = budgetAllocationRepository.save(ba);
        log.info("Budget utilized: {} from allocation {}", amount, id);
        return ba;
    }
}
