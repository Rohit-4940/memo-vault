package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoRouting;
import com.nexmemo.core.enums.MemoRoutingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoRoutingRepository extends JpaRepository<MemoRouting, Long> {
    List<MemoRouting> findByMemoId(Long memoId);
    List<MemoRouting> findByToBranchIdAndStatus(Long branchId, MemoRoutingStatus status);
}
