package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoWorkflowConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemoWorkflowConfigRepository extends JpaRepository<MemoWorkflowConfig, Long> {
    Optional<MemoWorkflowConfig> findByMemoTypeId(Long memoTypeId);
}
