package com.nexmemo.web.service;

import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.FiscalYearRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FiscalYearService {

    private final FiscalYearRepository fiscalYearRepository;

    @Cacheable(value = "referenceData", key = "'all-' + #root.targetClass.simpleName")
    @Transactional(readOnly = true)
    public List<FiscalYear> getAll() {
        return fiscalYearRepository.findAll();
    }

    @Transactional(readOnly = true)
    public FiscalYear getById(Long id) {
        return fiscalYearRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + id));
    }

    public FiscalYear create(FiscalYear fy) {
        fy.setIsActive(fy.getIsActive() != null && fy.getIsActive());
        fy.setStatus(fy.getStatus() != null ? fy.getStatus() : Status.ACTIVE);
        if (Boolean.TRUE.equals(fy.getIsActive())) {
            deactivateAllOthers();
        }
        fy = fiscalYearRepository.save(fy);
        log.info("FiscalYear created: {}", fy.getName());
        return fy;
    }

    public FiscalYear update(Long id, FiscalYear fy) {
        FiscalYear existing = fiscalYearRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + id));
        existing.setName(fy.getName());
        existing.setStartDate(fy.getStartDate());
        existing.setEndDate(fy.getEndDate());
        if (Boolean.TRUE.equals(fy.getIsActive())) {
            deactivateAllOthers();
            existing.setIsActive(true);
        } else if (fy.getIsActive() != null) {
            existing.setIsActive(false);
        }
        if (fy.getStatus() != null) existing.setStatus(fy.getStatus());
        existing = fiscalYearRepository.save(existing);
        log.info("FiscalYear updated: {}", existing.getName());
        return existing;
    }

    public void delete(Long id) {
        if (!fiscalYearRepository.existsById(id)) {
            throw new EntityNotFoundException("FiscalYear not found with id: " + id);
        }
        fiscalYearRepository.deleteById(id);
        log.info("FiscalYear deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public FiscalYear getActiveFiscalYear() {
        return fiscalYearRepository.findByIsActiveTrue()
                .orElseThrow(() -> new RuntimeException("No active fiscal year found"));
    }

    /** Phase 4b: non-throwing variant used when falling back gracefully during memo creation. */
    @Transactional(readOnly = true)
    public FiscalYear getActiveFiscalYearSafely() {
        return fiscalYearRepository.findByIsActiveTrue().orElse(null);
    }

    public FiscalYear setActiveFiscalYear(Long id) {
        FiscalYear fiscalYear = fiscalYearRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + id));
        deactivateAllOthers();
        fiscalYear.setIsActive(true);
        fiscalYear = fiscalYearRepository.save(fiscalYear);
        log.info("FiscalYear set as active: {}", fiscalYear.getName());
        return fiscalYear;
    }

    private void deactivateAllOthers() {
        fiscalYearRepository.findAll().stream()
                .filter(fy -> Boolean.TRUE.equals(fy.getIsActive()))
                .forEach(fy -> {
                    fy.setIsActive(false);
                    fiscalYearRepository.save(fy);
                });
    }
}
