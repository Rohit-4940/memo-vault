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

    /**
     * Resource-based check used by chat authorization: is this user assigned to any
     * workflow stage of the given memo? Derived query on the ManyToOne {@code assignee}.
     */
    boolean existsByMemoIdAndAssigneeId(Long memoId, Long assigneeId);
    List<WorkflowStageInstance> findByAssigneeIdAndStatus(Long assigneeId, WorkflowStageStatus status);

    /**
     * Is this user the assignee of a stage of the given memo currently in the given status?
     * Used by memo authorization to confirm the caller may act on the memo.
     */
    boolean existsByMemoIdAndAssigneeIdAndStatus(Long memoId, Long assigneeId, WorkflowStageStatus status);
    List<WorkflowStageInstance> findByStatusAndSlaDeadlineBefore(WorkflowStageStatus status, LocalDateTime deadline);
    long countByMemoIdAndStatus(Long memoId, WorkflowStageStatus status);
}
