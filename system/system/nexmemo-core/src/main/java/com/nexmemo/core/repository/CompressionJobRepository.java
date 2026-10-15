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

import java.util.List;
import java.util.Optional;

@Repository
public interface CompressionJobRepository extends JpaRepository<CompressionJob, Long>, JpaSpecificationExecutor<CompressionJob> {

    Page<CompressionJob> findByCreatedByOrderByCreatedDateDesc(Long createdBy, Pageable pageable);

    Page<CompressionJob> findByCreatedByAndStatusOrderByCreatedDateDesc(Long createdBy, CompressionStatus status, Pageable pageable);

    Page<CompressionJob> findByCreatedByAndOriginalNameContainingIgnoreCaseOrderByCreatedDateDesc(
            Long createdBy, String search, Pageable pageable);

    Optional<CompressionJob> findByIdAndCreatedBy(Long id, Long createdBy);

    List<CompressionJob> findByCreatedByAndBatchIdOrderByCreatedDateAsc(Long createdBy, String batchId);

    /**
     * Jobs that were left mid-flight (e.g. the process restarted). Used by the recovery sweep to
     * re-queue work instead of leaving rows stuck in {@code IN_PROGRESS} forever.
     */
    List<CompressionJob> findByStatus(CompressionStatus status);

    @Query("select coalesce(sum(j.originalSize), 0) from CompressionJob j where j.createdBy = :userId and j.status = com.nexmemo.core.enums.CompressionStatus.COMPLETED")
    long totalOriginalBytesByUser(@Param("userId") Long userId);

    @Query("select coalesce(sum(j.compressedSize), 0) from CompressionJob j where j.createdBy = :userId and j.status = com.nexmemo.core.enums.CompressionStatus.COMPLETED")
    long totalCompressedBytesByUser(@Param("userId") Long userId);
}
