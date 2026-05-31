package com.nexmemo.web.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.repository.ReferenceSequenceRepository;
import com.nexmemo.web.config.ReferenceProperties;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReferenceNumberService {

    private final ReferenceSequenceRepository sequenceRepository;
    private final ReferenceProperties referenceProperties;

    public static final String SEQUENCE_TYPE_MEMO = "MEMO";
    public static final String SCOPE_BRANCH = "BRANCH";
    public static final String SCOPE_DEPARTMENT = "DEPARTMENT";
    public static final String SCOPE_GLOBAL = "GLOBAL";

    public String generateMemoRefNo(FiscalYear fiscalYear, Branch branch, Department department) {
        log.debug("Generating memo ref no for FY={}, branch={}, dept={}",
                fiscalYear != null ? fiscalYear.getName() : null,
                branch != null ? branch.getBranchCode() : null,
                department != null ? department.getName() : null);

        String scopeType;
        Long scopeId;

        if (referenceProperties.isIncludeBranchCode() && branch != null) {
            scopeType = SCOPE_BRANCH;
            scopeId = branch.getId();
            log.debug("Using branch-scoped sequence: branchId={}", scopeId);
        } else if (referenceProperties.isIncludeDepartmentCode() && department != null) {
            scopeType = SCOPE_DEPARTMENT;
            scopeId = department.getId();
            log.debug("Using department-scoped sequence: deptId={}", scopeId);
        } else {
            scopeType = SCOPE_GLOBAL;
            scopeId = 0L;
            log.debug("Using global sequence");
        }

        ReferenceSequence seq = getOrCreateSequence(fiscalYear, scopeType, scopeId, SEQUENCE_TYPE_MEMO);
        seq.setLastSequence(seq.getLastSequence() + 1);
        sequenceRepository.save(seq);
        log.debug("Sequence incremented to {} for scope={}/{}/{}",
                seq.getLastSequence(), scopeType, scopeId, SEQUENCE_TYPE_MEMO);

        String refNo = buildReferenceNumber(seq, branch, department, fiscalYear);
        log.info("Generated reference number: {} (FY={}, scope={}/{}, seq={})",
                refNo,
                fiscalYear != null ? fiscalYear.getName() : "N/A",
                scopeType, scopeId,
                seq.getLastSequence());
        return refNo;
    }

    private ReferenceSequence getOrCreateSequence(FiscalYear fiscalYear, String scopeType, Long scopeId, String sequenceType) {
        Long fyId = fiscalYear != null ? fiscalYear.getId() : 0L;

        Optional<ReferenceSequence> existing = sequenceRepository.findForUpdate(fyId, scopeType, scopeId, sequenceType);
        if (existing.isPresent()) {
            log.trace("Found existing sequence: id={}, lastSeq={}", existing.get().getId(), existing.get().getLastSequence());
            return existing.get();
        }

        log.info("Creating new sequence: FY={}, scope={}/{}, type={}", fyId, scopeType, scopeId, sequenceType);
        ReferenceSequence newSeq = new ReferenceSequence();
        newSeq.setFiscalYear(fiscalYear);
        newSeq.setScopeType(scopeType);
        newSeq.setScopeId(scopeId);
        newSeq.setSequenceType(sequenceType);
        newSeq.setPrefix(referenceProperties.getBankCode());
        newSeq.setLastSequence(0L);
        newSeq.setSuffix("");
        ReferenceSequence saved = sequenceRepository.save(newSeq);
        log.debug("New sequence created with id={}", saved.getId());
        return saved;
    }

    private String buildReferenceNumber(ReferenceSequence seq, Branch branch, Department department, FiscalYear fiscalYear) {
        String bankCode = seq.getPrefix() != null && !seq.getPrefix().isBlank()
                ? seq.getPrefix() : referenceProperties.getBankCode();
        String branchCode = branch != null ? branch.getBranchCode() : "000";
        String deptCode = department != null ? department.getName().replaceAll("\\s+", "_").toUpperCase() : "000";
        String fyCode = extractFiscalYearCode(fiscalYear);
        String seqStr = String.format("%0" + referenceProperties.getPaddingLength() + "d", seq.getLastSequence());
        String yearStr = String.valueOf(Year.now().getValue());

        String format = referenceProperties.getFormat();
        String refNo = format
                .replace("{bankCode}", bankCode)
                .replace("{branchCode}", branchCode)
                .replace("{deptCode}", deptCode)
                .replace("{departmentCode}", deptCode)
                .replace("{fyCode}", fyCode)
                .replace("{fiscalYearCode}", fyCode)
                .replace("{year}", yearStr)
                .replace("{sequence}", seqStr)
                .replace("{seq}", seqStr);

        log.trace("Built reference number '{}' from format '{}'", refNo, format);
        return refNo;
    }

    private String extractFiscalYearCode(FiscalYear fiscalYear) {
        if (fiscalYear == null || fiscalYear.getName() == null) {
            return String.valueOf(Year.now().getValue());
        }
        String digits = fiscalYear.getName().replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            return digits.substring(0, 4);
        }
        return digits;
    }
}
