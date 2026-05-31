package com.nexmemo.core.repository;

import com.nexmemo.core.entity.ReferenceSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;
import java.util.Optional;

@Repository
public interface ReferenceSequenceRepository extends JpaRepository<ReferenceSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Query("SELECT rs FROM ReferenceSequence rs WHERE rs.fiscalYear.id = :fiscalYearId AND rs.scopeType = :scopeType AND rs.scopeId = :scopeId AND rs.sequenceType = :sequenceType")
    Optional<ReferenceSequence> findForUpdate(Long fiscalYearId, String scopeType, Long scopeId, String sequenceType);

    Optional<ReferenceSequence> findByFiscalYearIdAndScopeTypeAndScopeIdAndSequenceType(Long fiscalYearId, String scopeType, Long scopeId, String sequenceType);
}
