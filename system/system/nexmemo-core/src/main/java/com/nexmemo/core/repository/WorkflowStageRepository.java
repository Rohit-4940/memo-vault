package com.nexmemo.core.repository;

import com.nexmemo.core.entity.WorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowStageRepository extends JpaRepository<WorkflowStage, Long>, JpaSpecificationExecutor<WorkflowStage> {
    List<WorkflowStage> findAllByOrderByOrderIndexAsc();
}
