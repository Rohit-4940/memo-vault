package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentShareLinkRepository extends JpaRepository<DocumentShareLink, Long> {

    Optional<DocumentShareLink> findByToken(String token);

    List<DocumentShareLink> findByDocumentIdOrderByCreatedDateDesc(Long documentId);
}
