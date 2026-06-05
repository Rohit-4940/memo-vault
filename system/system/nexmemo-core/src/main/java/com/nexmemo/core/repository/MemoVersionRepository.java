package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemoVersionRepository extends JpaRepository<MemoVersion, Long>, JpaSpecificationExecutor<MemoVersion> {
    List<MemoVersion> findByMemoIdOrderByVersionNumberDesc(Long memoId);
    Optional<MemoVersion> findTopByMemoIdOrderByVersionNumberDesc(Long memoId);
}
