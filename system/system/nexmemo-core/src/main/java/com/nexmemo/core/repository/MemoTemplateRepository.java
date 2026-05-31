package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoTemplateRepository extends JpaRepository<MemoTemplate, Long> {
    List<MemoTemplate> findByDepartmentId(Long departmentId);
    List<MemoTemplate> findByMemoTypeId(Long memoTypeId);
    List<MemoTemplate> findByExpenseCategoryId(Long categoryId);
    List<MemoTemplate> findByStatus(com.nexmemo.core.enums.Status status);
}
