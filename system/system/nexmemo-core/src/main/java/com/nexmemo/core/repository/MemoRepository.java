package com.nexmemo.core.repository;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.enums.MemoStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoRepository extends JpaRepository<Memo, Long> {
    List<Memo> findByCreatedBy(Long userId);
    List<Memo> findByStatus(MemoStatus status);
    List<Memo> findByBranchId(Long branchId);
    List<Memo> findByCurrentApproverIdAndStatusNotIn(Long approverId, List<MemoStatus> statuses);
    Optional<Memo> findTopByOrderByIdDesc();
    List<Memo> findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(String title, String memoNo);
    List<Memo> findByCreatedByAndStatus(Long userId, MemoStatus status);
    @Query("SELECT m FROM Memo m WHERE m.isDeleted = false ORDER BY m.createdDate DESC")
    List<Memo> findAllActive();
}
