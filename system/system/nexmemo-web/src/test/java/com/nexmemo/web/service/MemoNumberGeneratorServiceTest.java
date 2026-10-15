package com.nexmemo.web.service;

import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.core.entity.ReferenceSequence;
import com.nexmemo.core.repository.FiscalYearRepository;
import com.nexmemo.core.repository.ReferenceSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4b — memo numbering uses a row-locked per-fiscal-year sequence producing the canonical
 * {@code NEXM-{fiscalYearId}-{sequence}} format.
 */
@ExtendWith(MockitoExtension.class)
class MemoNumberGeneratorServiceTest {

    @Mock private ReferenceSequenceRepository referenceSequenceRepository;
    @Mock private FiscalYearRepository fiscalYearRepository;

    @InjectMocks private MemoNumberGeneratorService service;

    @Test
    void generatesNumberFromExistingSequence_andIncrementsIt() {
        FiscalYear fy = fiscalYear(7L);
        ReferenceSequence seq = new ReferenceSequence(fy, "GLOBAL", 0L, "MEMO_NO", "NEXM", 5L, null);
        when(fiscalYearRepository.findById(7L)).thenReturn(Optional.of(fy));
        when(referenceSequenceRepository.findForUpdate(7L, "GLOBAL", 0L, "MEMO_NO")).thenReturn(Optional.of(seq));

        String number = service.generateMemoNumber(7L);

        assertThat(number).isEqualTo("NEXM-7-0006");
        assertThat(seq.getLastSequence()).isEqualTo(6L);
        verify(referenceSequenceRepository).save(seq);
    }

    @Test
    void createsSequenceWhenMissing_thenReturnsFirstNumber() {
        FiscalYear fy = fiscalYear(3L);
        when(fiscalYearRepository.findById(3L)).thenReturn(Optional.of(fy));
        when(referenceSequenceRepository.findForUpdate(3L, "GLOBAL", 0L, "MEMO_NO")).thenReturn(Optional.empty());
        when(referenceSequenceRepository.save(any(ReferenceSequence.class))).thenAnswer(inv -> inv.getArgument(0));

        String number = service.generateMemoNumber(3L);

        assertThat(number).isEqualTo("NEXM-3-0001");
        verify(referenceSequenceRepository, times(2)).save(any(ReferenceSequence.class));
    }

    private FiscalYear fiscalYear(Long id) {
        FiscalYear fy = new FiscalYear();
        fy.setId(id);
        fy.setName("FY");
        return fy;
    }
}