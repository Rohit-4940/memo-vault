package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemoDocumentRepository extends JpaRepository<MemoDocument, Long>, JpaSpecificationExecutor<MemoDocument> {
    List<MemoDocument> findByMemoId(Long memoId);
    void deleteByMemoId(Long memoId);
}
