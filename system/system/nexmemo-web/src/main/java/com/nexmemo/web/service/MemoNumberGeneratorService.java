package com.nexmemo.web.service;

import com.nexmemo.core.entity.ReferenceSequence;
import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.core.repository.ReferenceSequenceRepository;
import com.nexmemo.core.repository.FiscalYearRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemoNumberGeneratorService {

    private final ReferenceSequenceRepository referenceSequenceRepository;
    private final FiscalYearRepository fiscalYearRepository;

    @Transactional
    public String generateMemoNumber(Long fiscalYearId) {
        FiscalYear fy;
        if (fiscalYearId != null) {
            fy = fiscalYearRepository.findById(fiscalYearId)
                    .orElseThrow(() -> new RuntimeException("FiscalYear not found: " + fiscalYearId));
        } else {
            fy = fiscalYearRepository.findByIsActiveTrue()
                    .orElseThrow(() -> new RuntimeException("No active fiscal year found"));
        }

        ReferenceSequence seq = referenceSequenceRepository
                .findForUpdate(fy.getId(), "GLOBAL", 0L, "MEMO_NO")
                .orElseGet(() -> referenceSequenceRepository.save(
                        new ReferenceSequence(fy, "GLOBAL", 0L, "MEMO_NO",
                                "NEXM", 0L, null)));

        seq.setLastSequence(seq.getLastSequence() + 1);
        referenceSequenceRepository.save(seq);

        return String.format("NEXM-%d-%04d", fy.getId(), seq.getLastSequence());
    }
}
