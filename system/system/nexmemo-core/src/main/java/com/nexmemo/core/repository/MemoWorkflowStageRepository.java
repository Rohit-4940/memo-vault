package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoWorkflowStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoWorkflowStageRepository extends JpaRepository<MemoWorkflowStage, Long>, JpaSpecificationExecutor<MemoWorkflowStage> {
    List<MemoWorkflowStage> findByConfigIdOrderByStageOrderAsc(Long configId);
    void deleteByConfigId(Long configId);
}
