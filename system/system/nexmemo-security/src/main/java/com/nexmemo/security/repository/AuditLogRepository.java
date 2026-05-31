package com.nexmemo.security.repository;

import com.nexmemo.core.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByPerformedByIdOrderByCreatedDateDesc(Long userId);

    @Query("SELECT a FROM AuditLog a WHERE a.entityType = :type AND a.entityId = :id ORDER BY a.createdDate DESC")
    List<AuditLog> findByEntity(@Param("type") String type, @Param("id") Long id);

    @Query("SELECT a FROM AuditLog a WHERE a.action = :action AND a.createdDate > :since ORDER BY a.createdDate DESC")
    List<AuditLog> findRecentByAction(@Param("action") String action, @Param("since") LocalDateTime since);

    @Query("SELECT a FROM AuditLog a WHERE a.performedById = :userId AND a.createdDate BETWEEN :from AND :to ORDER BY a.createdDate DESC")
    List<AuditLog> findByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = "SELECT a FROM AuditLog a WHERE " +
            "(:action IS NULL OR a.action LIKE %:action%) AND " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:username IS NULL OR a.performedByUsername LIKE %:username%) AND " +
            "(:dateFrom IS NULL OR a.createdDate >= :dateFrom) AND " +
            "(:dateTo IS NULL OR a.createdDate <= :dateTo) " +
            "ORDER BY a.createdDate DESC")
    Page<AuditLog> findFiltered(
            @Param("action") String action,
            @Param("status") AuditLog.AuditStatus status,
            @Param("username") String username,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable);
}
