package com.nexmemo.core.repository;

import com.nexmemo.core.entity.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByMemoIdOrderByApprovalLevelAsc(Long memoId);
    List<ApprovalHistory> findByApproverId(Long approverId);
    long countByMemoIdAndAction(Long memoId, com.nexmemo.core.enums.MemoStatus action);
}
