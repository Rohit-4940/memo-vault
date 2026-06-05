package com.nexmemo.core.repository;

import com.nexmemo.core.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long>, JpaSpecificationExecutor<ApprovalHistory> {
    List<ApprovalHistory> findByMemoIdOrderByApprovalLevelAsc(Long memoId);
    List<ApprovalHistory> findByApproverId(Long approverId);
    long countByMemoIdAndAction(Long memoId, com.nexmemo.core.enums.MemoStatus action);

    // ── Command Center analytics ─────────────────────────────────────────────
    // JPQL-only; time calculations done in Java for database portability

    @Query("SELECT a.approver.id, a.approver.name, a.actionDate, m.submittedDate " +
           "FROM ApprovalHistory a JOIN a.memo m WHERE a.action = 'APPROVED'")
    List<Object[]> approverTimings();

    @Query("SELECT a.id, a.action, a.actionDate, a.remarks, m.memoNo, m.title, u.name " +
           "FROM ApprovalHistory a JOIN a.memo m JOIN a.approver u ORDER BY a.actionDate DESC")
    List<Object[]> recentActivity();
}
