package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoCc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemoCcRepository extends JpaRepository<MemoCc, Long>, JpaSpecificationExecutor<MemoCc> {
    List<MemoCc> findByMemoId(Long memoId);
    List<MemoCc> findByUserId(Long userId);
    List<MemoCc> findByUserIdAndAcknowledgedFalse(Long userId);
    Optional<MemoCc> findByMemoIdAndUserId(Long memoId, Long userId);
    long countByMemoIdAndAcknowledgedFalse(Long memoId);
}
