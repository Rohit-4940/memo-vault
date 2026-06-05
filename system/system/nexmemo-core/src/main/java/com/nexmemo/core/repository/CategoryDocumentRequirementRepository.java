package com.nexmemo.core.repository;

import com.nexmemo.core.entity.CategoryDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryDocumentRequirementRepository extends JpaRepository<CategoryDocumentRequirement, Long>, JpaSpecificationExecutor<CategoryDocumentRequirement> {
    List<CategoryDocumentRequirement> findByCategoryId(Long categoryId);
}
