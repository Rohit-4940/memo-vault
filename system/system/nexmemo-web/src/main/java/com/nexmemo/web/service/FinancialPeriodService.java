package com.nexmemo.web.service;

import com.nexmemo.core.entity.FinancialPeriod;
import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.core.enums.FinancialPeriodStatus;
import com.nexmemo.core.repository.FinancialPeriodRepository;
import com.nexmemo.core.repository.FiscalYearRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Financial period administration (Phase 4b) — open / close / lock lifecycle for periods within a
 * fiscal year, plus the memo-creation gate that consults the currently open period.
 *
 * <p>Design: periods are strictly controlled once configured (at most one {@code OPEN} at a time,
 * dates must fall inside their fiscal year). Memo creation, however, fails open when periods have
 * never been configured: it then falls back to the active fiscal year, or — when even that is
 * missing — to a legacy global memo number.</p>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FinancialPeriodService {

    private final FinancialPeriodRepository financialPeriodRepository;
    private final FiscalYearRepository fiscalYearRepository;
    private final FiscalYearService fiscalYearService;

    @Transactional(readOnly = true)
    public List<FinancialPeriod> getAll() {
        return financialPeriodRepository.findAll();
    }

    @Transactional(readOnly = true)
    public FinancialPeriod getById(Long id) {
        return financialPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("FinancialPeriod not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<FinancialPeriod> getByFiscalYear(Long fiscalYearId) {
        return financialPeriodRepository.findByFiscalYearId(fiscalYearId);
    }

    @Transactional(readOnly = true)
    public Optional<FinancialPeriod> getActiveOpenPeriod() {
        return financialPeriodRepository.findFirstByStatusOrderByEndDateDesc(FinancialPeriodStatus.OPEN);
    }

    // ── CRUD ────────────────────────────────────────────────────────────────────────────────

    public FinancialPeriod create(FinancialPeriod period) {
        FiscalYear fiscalYear = requireFiscalYear(period);
        validateDates(period, fiscalYear);
        if (period.getStatus() == null) {
            period.setStatus(FinancialPeriodStatus.CLOSED);
        }
        if (period.getStatus() == FinancialPeriodStatus.OPEN) {
            closeAllOpenPeriods();
        }
        period.setFiscalYear(fiscalYear);
        FinancialPeriod saved = financialPeriodRepository.save(period);
        log.info("FinancialPeriod created: id={} name={} status={} fy={}",
                saved.getId(), saved.getName(), saved.getStatus(), saved.getFiscalYear().getId());
        return saved;
    }

    public FinancialPeriod update(Long id, FinancialPeriod updated) {
        FinancialPeriod existing = getById(id);
        FiscalYear fiscalYear = requireFiscalYear(updated);
        validateDates(updated, fiscalYear);
        existing.setName(updated.getName());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setFiscalYear(fiscalYear);
        if (updated.getStatus() != null) {
            if (updated.getStatus() == FinancialPeriodStatus.OPEN) {
                closeAllOpenPeriods();
            }
            if (existing.getStatus() == FinancialPeriodStatus.LOCKED
                    && updated.getStatus() != FinancialPeriodStatus.LOCKED) {
                throw new IllegalStateException("A locked financial period cannot be changed");
            }
            existing.setStatus(updated.getStatus());
        }
        FinancialPeriod saved = financialPeriodRepository.save(existing);
        log.info("FinancialPeriod updated: id={} name={} status={}", saved.getId(), saved.getName(), saved.getStatus());
        return saved;
    }

    public void delete(Long id) {
        FinancialPeriod period = getById(id);
        if (period.getStatus() == FinancialPeriodStatus.OPEN) {
            throw new IllegalStateException("An OPEN financial period cannot be deleted; close or lock it first");
        }
        financialPeriodRepository.deleteById(id);
        log.info("FinancialPeriod deleted: id={}", id);
    }

    // ── Lifecycle transitions ───────────────────────────────────────────────────────────────

    public FinancialPeriod open(Long id) {
        FinancialPeriod period = getById(id);
        if (period.getStatus() == FinancialPeriodStatus.LOCKED) {
            throw new IllegalStateException("A LOCKED financial period cannot be reopened");
        }
        closeAllOpenPeriods();
        period.setStatus(FinancialPeriodStatus.OPEN);
        FinancialPeriod saved = financialPeriodRepository.save(period);
        log.info("FinancialPeriod opened: id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    public FinancialPeriod close(Long id) {
        FinancialPeriod period = getById(id);
        if (period.getStatus() != FinancialPeriodStatus.OPEN) {
            throw new IllegalStateException("Only an OPEN financial period can be closed");
        }
        period.setStatus(FinancialPeriodStatus.CLOSED);
        FinancialPeriod saved = financialPeriodRepository.save(period);
        log.info("FinancialPeriod closed: id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    public FinancialPeriod lock(Long id) {
        FinancialPeriod period = getById(id);
        if (period.getStatus() == FinancialPeriodStatus.LOCKED) {
            return period; // idempotent
        }
        period.setStatus(FinancialPeriodStatus.LOCKED);
        FinancialPeriod saved = financialPeriodRepository.save(period);
        log.info("FinancialPeriod locked: id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    // ── Memo-creation gate (Phase 4b) ────────────────────────────────────────────────────────

    /**
     * Resolves the fiscal year a newly created memo belongs to, enforcing the period gate:
     * <ol>
     *   <li>If an OPEN period covers today → its fiscal year (strict).</li>
     *   <li>If OPEN periods exist but none covers today → reject (periods are configured now).</li>
     *   <li>If no OPEN period exists at all (periods never configured) → active fiscal year, or
     *       {@code null} so the caller can fall back to legacy global numbering.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public FiscalYear resolveCreationFiscalYear() {
        LocalDate today = LocalDate.now();

        Optional<FinancialPeriod> openCoveringToday = financialPeriodRepository
                .findByStatus(FinancialPeriodStatus.OPEN).stream()
                .filter(p -> !p.getStartDate().isAfter(today) && !p.getEndDate().isBefore(today))
                .findFirst();
        if (openCoveringToday.isPresent()) {
            return openCoveringToday.get().getFiscalYear();
        }

        if (financialPeriodRepository.existsByStatus(FinancialPeriodStatus.OPEN)) {
            throw new IllegalStateException(
                    "No financial period is open for today (" + today + "); memo creation is not allowed");
        }

        FiscalYear activeFy = fiscalYearService.getActiveFiscalYearSafely();
        if (activeFy != null) {
            log.debug("No financial periods configured; falling back to active fiscal year {}", activeFy.getId());
        } else {
            log.warn("No active fiscal year and no open financial period; using legacy memo numbering");
        }
        return activeFy;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private FiscalYear requireFiscalYear(FinancialPeriod period) {
        Long fiscalYearId = period.getFiscalYear() != null ? period.getFiscalYear().getId() : null;
        if (fiscalYearId == null) {
            throw new IllegalArgumentException("Financial period must reference a fiscal year");
        }
        return fiscalYearRepository.findById(fiscalYearId)
                .orElseThrow(() -> new EntityNotFoundException("FiscalYear not found with id: " + fiscalYearId));
    }

    private void validateDates(FinancialPeriod period, FiscalYear fiscalYear) {
        if (period.getStartDate() == null || period.getEndDate() == null) {
            throw new IllegalArgumentException("Financial period requires both start and end dates");
        }
        if (period.getEndDate().isBefore(period.getStartDate())) {
            throw new IllegalArgumentException("End date must be on or after the start date");
        }
        if (fiscalYear.getStartDate() != null && fiscalYear.getEndDate() != null
                && (period.getStartDate().isBefore(fiscalYear.getStartDate())
                    || period.getEndDate().isAfter(fiscalYear.getEndDate()))) {
            throw new IllegalArgumentException("Financial period must fall within fiscal year '"
                    + fiscalYear.getName() + "' (" + fiscalYear.getStartDate() + " to " + fiscalYear.getEndDate() + ")");
        }
    }

    private void closeAllOpenPeriods() {
        financialPeriodRepository.findByStatus(FinancialPeriodStatus.OPEN).forEach(period -> {
            period.setStatus(FinancialPeriodStatus.CLOSED);
            financialPeriodRepository.save(period);
            log.debug("Auto-closed previously open financial period id={}", period.getId());
        });
    }
}