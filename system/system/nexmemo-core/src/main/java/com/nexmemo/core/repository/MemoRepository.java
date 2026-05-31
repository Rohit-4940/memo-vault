package com.nexmemo.core.repository;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.enums.MemoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoRepository extends JpaRepository<Memo, Long> {
    List<Memo> findByCreatedBy(Long userId);
    Page<Memo> findByCreatedBy(Long userId, Pageable pageable);
    List<Memo> findByStatus(MemoStatus status);
    Page<Memo> findByStatus(MemoStatus status, Pageable pageable);
    List<Memo> findByBranchId(Long branchId);
    List<Memo> findByCurrentApproverIdAndStatusNotIn(Long approverId, List<MemoStatus> statuses);
    Page<Memo> findByCurrentApproverIdAndStatusNotIn(Long approverId, List<MemoStatus> statuses, Pageable pageable);
    Optional<Memo> findTopByOrderByIdDesc();
    List<Memo> findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(String title, String memoNo);
    Page<Memo> findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(String title, String memoNo, Pageable pageable);
    List<Memo> findByCreatedByAndStatus(Long userId, MemoStatus status);
    Page<Memo> findByCreatedByAndStatus(Long userId, MemoStatus status, Pageable pageable);
    @Query("SELECT m FROM Memo m WHERE m.isDeleted = false ORDER BY m.createdDate DESC")
    List<Memo> findAllActive();
    @Query("SELECT m FROM Memo m WHERE m.isDeleted = false ORDER BY m.createdDate DESC")
    Page<Memo> findAllActive(Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.amount), 0) FROM Memo m WHERE m.isDeleted = false AND m.status = :status")
    java.math.BigDecimal sumAmountByStatus(@Param("status") MemoStatus status);

    @Query("SELECT m.expenseCategory, COUNT(m) FROM Memo m WHERE m.isDeleted = false AND m.expenseCategory IS NOT NULL GROUP BY m.expenseCategory")
    List<Object[]> countByExpenseCategory();

    @Query("SELECT m.status, COUNT(m) FROM Memo m WHERE m.isDeleted = false GROUP BY m.status")
    List<Object[]> countByStatus();

    @Query("SELECT FUNCTION('MONTH', m.createdDate), COUNT(m) FROM Memo m WHERE m.isDeleted = false AND YEAR(m.createdDate) = :year GROUP BY FUNCTION('MONTH', m.createdDate)")
    List<Object[]> countByMonth(@Param("year") int year);

    long countByIsDeletedFalse();
    long countByStatusAndIsDeletedFalse(MemoStatus status);
    long countByCurrentApproverIdAndIsDeletedFalse(Long approverId);
}
