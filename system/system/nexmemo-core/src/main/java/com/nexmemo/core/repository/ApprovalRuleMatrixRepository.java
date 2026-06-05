package com.nexmemo.core.repository;

import com.nexmemo.core.entity.ApprovalRuleMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRuleMatrixRepository extends JpaRepository<ApprovalRuleMatrix, Long>, JpaSpecificationExecutor<ApprovalRuleMatrix> {
    List<ApprovalRuleMatrix> findByCategoryIdOrderByPriorityAsc(Long categoryId);
    List<ApprovalRuleMatrix> findByCategoryIdAndStageIdOrderByPriorityAsc(Long categoryId, Long stageId);
    List<ApprovalRuleMatrix> findByUserId(Long userId);
    List<ApprovalRuleMatrix> findByRoleId(Long roleId);
}
