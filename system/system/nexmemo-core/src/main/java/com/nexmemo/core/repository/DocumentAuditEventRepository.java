package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentAuditEventRepository extends JpaRepository<DocumentAuditEvent, Long> {

    List<DocumentAuditEvent> findByDocumentIdOrderByCreatedDateDesc(Long documentId);
}
