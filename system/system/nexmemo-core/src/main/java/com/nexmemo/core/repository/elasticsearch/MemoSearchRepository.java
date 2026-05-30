package com.nexmemo.core.repository.elasticsearch;

import com.nexmemo.core.document.MemoDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoSearchRepository extends ElasticsearchRepository<MemoDocument, Long> {
    List<MemoDocument> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(String title, String description);
    List<MemoDocument> findByStatus(String status);
    List<MemoDocument> findByCreatedBy(String createdBy);
}
