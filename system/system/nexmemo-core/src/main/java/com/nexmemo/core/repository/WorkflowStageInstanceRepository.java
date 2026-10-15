package com.nexmemo.core.repository;

import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.WorkflowStageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowStageInstanceRepository extends JpaRepository<WorkflowStageInstance, Long>, JpaSpecificationExecutor<WorkflowStageInstance> {
    List<WorkflowStageInstance> findByMemoIdOrderByEnteredAtAsc(Long memoId);
    Optional<WorkflowStageInstance> findByMemoIdAndStageId(Long memoId, Long stageId);

    /**
     * Pessimistically locks a stage instance while a workflow decision is being made, so that a
     * concurrent approval by another eligible member of the assignee pool cannot double-advance
     * the memo (Phase 5).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from WorkflowStageInstance i where i.id = :id")
    Optional<WorkflowStageInstance> findByIdForUpdate(@Param("id") Long id);

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
