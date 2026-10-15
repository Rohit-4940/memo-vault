package com.nexmemo.web.service;

import com.nexmemo.core.entity.FinancialPeriod;
import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.core.enums.FinancialPeriodStatus;
import com.nexmemo.core.repository.FinancialPeriodRepository;
import com.nexmemo.core.repository.FiscalYearRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4b — financial period lifecycle: single-open invariant, date validation against the fiscal
 * year, close/lock transitions, and the memo-creation gate (fail open when unconfigured).
 */
@ExtendWith(MockitoExtension.class)
class FinancialPeriodServiceTest {

    @Mock private FinancialPeriodRepository financialPeriodRepository;
    @Mock private FiscalYearRepository fiscalYearRepository;
    @Mock private FiscalYearService fiscalYearService;

    @InjectMocks private FinancialPeriodService service;

    private static final LocalDate FY_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate FY_END = LocalDate.of(2026, 12, 31);

    private FiscalYear fiscalYear(Long id) {
        FiscalYear fy = new FiscalYear();
        fy.setId(id);
        fy.setName("FY 2082/83");
        fy.setStartDate(FY_START);
        fy.setEndDate(FY_END);
        fy.setIsActive(true);
        return fy;
    }

    private FinancialPeriod period(Long id, FinancialPeriodStatus status, LocalDate start, LocalDate end, FiscalYear fy) {
        FinancialPeriod p = new FinancialPeriod();
        p.setId(id);
        p.setName("P" + id);
        p.setStatus(status);
        p.setStartDate(start);
        p.setEndDate(end);
        p.setFiscalYear(fy);
        return p;
    }

    @Test
    void createWithOpenStatus_closesOtherOpenPeriods() {
        FiscalYear fy = fiscalYear(1L);
        FinancialPeriod existingOpen = period(9L, FinancialPeriodStatus.OPEN, FY_START, LocalDate.of(2026, 6, 30), fy);
        when(fiscalYearRepository.findById(1L)).thenReturn(Optional.of(fy));
        when(financialPeriodRepository.findByStatus(FinancialPeriodStatus.OPEN)).thenReturn(List.of(existingOpen));
        when(financialPeriodRepository.save(any(FinancialPeriod.class))).thenAnswer(inv -> inv.getArgument(0));

        FinancialPeriod incoming = period(null, FinancialPeriodStatus.OPEN,
                LocalDate.of(2026, 7, 1), FY_END, fy);
        service.create(incoming);

        assertThat(existingOpen.getStatus()).isEqualTo(FinancialPeriodStatus.CLOSED);
        verify(financialPeriodRepository, times(2)).save(any(FinancialPeriod.class));
    }

    @Test
    void create_rejectsPeriodOutsideFiscalYear() {
        FiscalYear fy = fiscalYear(1L);
        when(fiscalYearRepository.findById(1L)).thenReturn(Optional.of(fy));

        FinancialPeriod outOfBounds = period(null, FinancialPeriodStatus.CLOSED,
                FY_START, LocalDate.of(2027, 6, 30), fy);

        assertThatThrownBy(() -> service.create(outOfBounds))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must fall within fiscal year");
    }

    @Test
    void create_rejectsEndBeforeStart() {
        FiscalYear fy = fiscalYear(1L);
        when(fiscalYearRepository.findById(1L)).thenReturn(Optional.of(fy));

        FinancialPeriod inverted = period(null, FinancialPeriodStatus.CLOSED,
                LocalDate.of(2026, 6, 30), LocalDate.of(2026, 1, 1), fy);

        assertThatThrownBy(() -> service.create(inverted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("on or after the start date");
    }

    @Test
    void close_requiresOpenStatus() {
        FiscalYear fy = fiscalYear(1L);
        FinancialPeriod closed = period(2L, FinancialPeriodStatus.CLOSED, FY_START, FY_END, fy);
        when(financialPeriodRepository.findById(2L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.close(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only an OPEN");
    }

    @Test
    void lock_isIdempotent_andLockedCannotBeReopened() {
        FiscalYear fy = fiscalYear(1L);
        FinancialPeriod locked = period(3L, FinancialPeriodStatus.LOCKED, FY_START, FY_END, fy);
        when(financialPeriodRepository.findById(3L)).thenReturn(Optional.of(locked));

        assertThat(service.lock(3L).getStatus()).isEqualTo(FinancialPeriodStatus.LOCKED);
        verify(financialPeriodRepository, never()).save(any(FinancialPeriod.class));

        assertThatThrownBy(() -> service.open(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LOCKED");
    }

    @Test
    void delete_rejectsOpenPeriod() {
        FiscalYear fy = fiscalYear(1L);
        FinancialPeriod open = period(4L, FinancialPeriodStatus.OPEN, FY_START, FY_END, fy);
        when(financialPeriodRepository.findById(4L)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.delete(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPEN");
        verify(financialPeriodRepository, never()).deleteById(4L);
    }

    @Test
    void resolveCreationFiscalYear_usesOpenPeriodCoveringToday() {
        FiscalYear fy = fiscalYear(1L);
        LocalDate today = LocalDate.now();
        FinancialPeriod open = period(1L, FinancialPeriodStatus.OPEN, today.minusDays(1), today.plusDays(1), fy);
        when(financialPeriodRepository.findByStatus(FinancialPeriodStatus.OPEN)).thenReturn(List.of(open));

        assertThat(service.resolveCreationFiscalYear()).isSameAs(fy);
    }

    @Test
    void resolveCreationFiscalYear_rejectsWhenOpenPeriodsExistButNoneCoverToday() {
        FiscalYear fy = fiscalYear(1L);
        LocalDate today = LocalDate.now();
        FinancialPeriod past = period(1L, FinancialPeriodStatus.OPEN, today.minusDays(10), today.minusDays(1), fy);
        when(financialPeriodRepository.findByStatus(FinancialPeriodStatus.OPEN)).thenReturn(List.of(past));
        when(financialPeriodRepository.existsByStatus(FinancialPeriodStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.resolveCreationFiscalYear())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is open for today");
    }

    @Test
    void resolveCreationFiscalYear_fallsBackToActiveFiscalYearWhenNeverConfigured() {
        FiscalYear fy = fiscalYear(1L);
        when(financialPeriodRepository.findByStatus(FinancialPeriodStatus.OPEN)).thenReturn(List.of());
        when(fiscalYearService.getActiveFiscalYearSafely()).thenReturn(fy);

        assertThat(service.resolveCreationFiscalYear()).isSameAs(fy);
    }

    @Test
    void resolveCreationFiscalYear_returnsNullWhenNothingConfigured() {
        when(financialPeriodRepository.findByStatus(FinancialPeriodStatus.OPEN)).thenReturn(List.of());
        when(fiscalYearService.getActiveFiscalYearSafely()).thenReturn(null);

        assertThat(service.resolveCreationFiscalYear()).isNull();
    }
}