package com.nexmemo.core.repository;

import com.nexmemo.core.entity.TemplateApprovalLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TemplateApprovalLevelRepository extends JpaRepository<TemplateApprovalLevel, Long> {
    List<TemplateApprovalLevel> findByTemplateIdOrderByLevelOrderAsc(Long templateId);
    void deleteByTemplateId(Long templateId);
}
