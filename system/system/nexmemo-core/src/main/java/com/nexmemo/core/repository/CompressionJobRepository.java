package com.nexmemo.core.repository;

import com.nexmemo.core.entity.CompressionJob;
import com.nexmemo.core.enums.CompressionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompressionJobRepository extends JpaRepository<CompressionJob, Long>, JpaSpecificationExecutor<CompressionJob> {

    Page<CompressionJob> findByCreatedByOrderByCreatedDateDesc(Long createdBy, Pageable pageable);

    Page<CompressionJob> findByCreatedByAndStatusOrderByCreatedDateDesc(Long createdBy, CompressionStatus status, Pageable pageable);

    Page<CompressionJob> findByCreatedByAndOriginalNameContainingIgnoreCaseOrderByCreatedDateDesc(Long createdBy, String search, Pageable pageable);

    Optional<CompressionJob> findByIdAndCreatedBy(Long id, Long createdBy);

    long countByCreatedBy(Long createdBy);

    @Query("SELECT COALESCE(SUM(c.originalSize), 0) FROM CompressionJob c WHERE c.createdBy = :userId AND c.status = 'COMPLETED'")
    long totalOriginalBytesByUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(c.compressedSize), 0) FROM CompressionJob c WHERE c.createdBy = :userId AND c.status = 'COMPLETED'")
    long totalCompressedBytesByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM CompressionJob c WHERE c.createdBy = :userId AND c.status = 'COMPLETED'")
    long completedCountByUser(@Param("userId") Long userId);
}
