package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentPermission;
import com.nexmemo.core.enums.PermissionSubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentPermissionRepository extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findByDocumentId(Long documentId);

    Optional<DocumentPermission> findByDocumentIdAndSubjectTypeAndSubjectId(
            Long documentId, PermissionSubjectType subjectType, Long subjectId);

    void deleteByDocumentId(Long documentId);
}
