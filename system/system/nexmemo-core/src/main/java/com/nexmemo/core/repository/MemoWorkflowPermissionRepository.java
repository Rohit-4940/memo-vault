package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoWorkflowPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoWorkflowPermissionRepository extends JpaRepository<MemoWorkflowPermission, Long> {
    List<MemoWorkflowPermission> findByMemoTypeId(Long memoTypeId);
    void deleteByMemoTypeId(Long memoTypeId);
}
