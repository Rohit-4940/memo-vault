package com.nexmemo.core.repository;

import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.WorkflowStageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowStageInstanceRepository extends JpaRepository<WorkflowStageInstance, Long>, JpaSpecificationExecutor<WorkflowStageInstance> {
    List<WorkflowStageInstance> findByMemoIdOrderByEnteredAtAsc(Long memoId);
    Optional<WorkflowStageInstance> findByMemoIdAndStageId(Long memoId, Long stageId);
    List<WorkflowStageInstance> findByAssigneeIdAndStatus(Long assigneeId, WorkflowStageStatus status);
    List<WorkflowStageInstance> findByStatusAndSlaDeadlineBefore(WorkflowStageStatus status, LocalDateTime deadline);
    long countByMemoIdAndStatus(Long memoId, WorkflowStageStatus status);
}
