package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentTagRepository extends JpaRepository<DocumentTag, Long> {

    List<DocumentTag> findByDocumentId(Long documentId);

    List<DocumentTag> findByDocumentIdIn(Collection<Long> documentIds);

    void deleteByDocumentIdAndTag(Long documentId, String tag);

    void deleteByDocumentId(Long documentId);
}
