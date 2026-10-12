package com.nexmemo.core.repository;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.enums.MemoStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Memo repository with JOIN FETCH on all list queries to prevent N+1.
 *
 * PERFORMANCE: Every query that returns Memo objects uses JOIN FETCH
 * to eagerly load the most commonly accessed associations:
 * - memoType
 * - department
 * - branch
 * - createdByUser
 * - currentApprover
 * - expenseCategory
 * - fiscalYear
 *
 * This eliminates the N+1 query problem where accessing each memo's
 * associated entities triggers a separate database query.
 */
@Repository
public interface MemoRepository extends JpaRepository<Memo, Long>, JpaSpecificationExecutor<Memo> {

    String FETCH_GRAPH = """
            JOIN FETCH m.memoType
            JOIN FETCH m.department
            JOIN FETCH m.branch
            JOIN FETCH m.createdByUser
            LEFT JOIN FETCH m.currentApprover
            LEFT JOIN FETCH m.expenseCategory
            LEFT JOIN FETCH m.fiscalYear
            """;

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.createdBy = :userId ORDER BY m.createdDate DESC")
    List<Memo> findByCreatedBy(@Param("userId") Long userId);

    @Query(value = "SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.createdBy = :userId",
           countQuery = "SELECT COUNT(m) FROM Memo m WHERE m.createdBy = :userId")
    Page<Memo> findByCreatedBy(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.status = :status ORDER BY m.createdDate DESC")
    List<Memo> findByStatus(@Param("status") MemoStatus status);

    @Query(value = "SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.status = :status",
           countQuery = "SELECT COUNT(m) FROM Memo m WHERE m.status = :status")
    Page<Memo> findByStatus(@Param("status") MemoStatus status, Pageable pageable);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.branch.id = :branchId ORDER BY m.createdDate DESC")
    List<Memo> findByBranchId(@Param("branchId") Long branchId);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.currentApprover.id = :approverId AND m.status NOT IN :statuses ORDER BY m.createdDate DESC")
    List<Memo> findByCurrentApproverIdAndStatusNotIn(@Param("approverId") Long approverId, @Param("statuses") List<MemoStatus> statuses);

    @Query(value = "SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.currentApprover.id = :approverId AND m.status NOT IN :statuses",
           countQuery = "SELECT COUNT(m) FROM Memo m WHERE m.currentApprover.id = :approverId AND m.status NOT IN :statuses")
    Page<Memo> findByCurrentApproverIdAndStatusNotIn(@Param("approverId") Long approverId, @Param("statuses") List<MemoStatus> statuses, Pageable pageable);

    Optional<Memo> findTopByOrderByIdDesc();

    /**
     * Loads a memo under a pessimistic write lock. Used by workflow-decision paths so that two
     * concurrent approve/reject/return calls are serialised: the second transaction blocks until
     * the first commits, then observes the new status and is rejected by the status guard. This
     * prevents concurrent decisions from producing inconsistent workflow states.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Memo m WHERE m.id = :id")
    Optional<Memo> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(m.memoNo) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ORDER BY m.createdDate DESC")
    List<Memo> findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(@Param("searchTerm") String searchTerm);

    @Query(value = "SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(m.memoNo) LIKE LOWER(CONCAT('%', :searchTerm, '%'))",
           countQuery = "SELECT COUNT(m) FROM Memo m WHERE LOWER(m.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(m.memoNo) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Memo> findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.createdBy = :userId AND m.status = :status ORDER BY m.createdDate DESC")
    List<Memo> findByCreatedByAndStatus(@Param("userId") Long userId, @Param("status") MemoStatus status);

    @Query(value = "SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.createdBy = :userId AND m.status = :status",
           countQuery = "SELECT COUNT(m) FROM Memo m WHERE m.createdBy = :userId AND m.status = :status")
    Page<Memo> findByCreatedByAndStatus(@Param("userId") Long userId, @Param("status") MemoStatus status, Pageable pageable);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.isDeleted = false ORDER BY m.createdDate DESC")
    List<Memo> findAllActive();

    @Query(value = "SELECT m FROM Memo m " + FETCH_GRAPH + " WHERE m.isDeleted = false",
           countQuery = "SELECT COUNT(m) FROM Memo m WHERE m.isDeleted = false")
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

    @Query("SELECT COUNT(m) FROM Memo m WHERE m.createdBy = :userId AND m.isDeleted = false")
    long countByCreatedBy(@Param("userId") Long userId);

    // ── Command Center analytics ─────────────────────────────────────────────
    // JPQL-only; time calculations done in Java for database portability

    @Query("SELECT d.name, mt.name, m.submittedDate, m.approvedDate " +
           "FROM Memo m JOIN m.department d JOIN m.memoType mt " +
           "WHERE m.status = 'APPROVED' AND m.submittedDate IS NOT NULL AND m.approvedDate IS NOT NULL")
    List<Object[]> approvedMemosWithDeptAndType();

    @Query("SELECT m.priority, COUNT(m.id), " +
           "SUM(CASE WHEN m.slaDeadline IS NOT NULL AND m.approvedDate <= m.slaDeadline THEN 1 ELSE 0 END) " +
           "FROM Memo m WHERE m.status = 'APPROVED' AND m.slaDeadline IS NOT NULL GROUP BY m.priority")
    List<Object[]> slaComplianceByPriority();

    @Query("SELECT d.name, " +
           "COUNT(m.id), " +
           "SUM(CASE WHEN m.status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN m.status = 'APPROVED' THEN 1 ELSE 0 END) " +
           "FROM Memo m JOIN m.department d WHERE m.isDeleted = false GROUP BY d.name")
    List<Object[]> departmentCounts();

    @Query("SELECT d.name, m.submittedDate, m.approvedDate " +
           "FROM Memo m JOIN m.department d " +
           "WHERE m.status = 'APPROVED' AND m.submittedDate IS NOT NULL AND m.approvedDate IS NOT NULL")
    List<Object[]> departmentApprovalTimes();

    @Query("SELECT FUNCTION('YEAR', m.createdDate), FUNCTION('MONTH', m.createdDate), COUNT(m.id) " +
           "FROM Memo m WHERE m.isDeleted = false AND m.createdDate >= :since " +
           "GROUP BY FUNCTION('YEAR', m.createdDate), FUNCTION('MONTH', m.createdDate) " +
           "ORDER BY FUNCTION('YEAR', m.createdDate), FUNCTION('MONTH', m.createdDate)")
    List<Object[]> countByYearMonth(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH +
           " WHERE m.status = 'PENDING_APPROVAL' AND m.slaDeadline IS NOT NULL AND m.slaDeadline <= :threshold " +
           "ORDER BY m.slaDeadline ASC")
    List<Memo> findAtRiskMemos(@Param("threshold") java.time.LocalDateTime threshold);

    @Query("SELECT m FROM Memo m " + FETCH_GRAPH +
           " WHERE m.status = 'PENDING_APPROVAL' AND m.slaDeadline IS NOT NULL " +
           "ORDER BY m.slaDeadline ASC")
    List<Memo> findAllPendingWithDeadlines();
}
