package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentTransfer;
import com.nexmemo.core.enums.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocumentTransferRepository extends JpaRepository<DocumentTransfer, Long>, JpaSpecificationExecutor<DocumentTransfer> {

    Page<DocumentTransfer> findBySenderIdOrderByCreatedDateDesc(Long senderId, Pageable pageable);

    Page<DocumentTransfer> findByRecipientIdOrderByCreatedDateDesc(Long recipientId, Pageable pageable);

    Page<DocumentTransfer> findBySenderIdAndTransferStatusOrderByCreatedDateDesc(Long senderId, TransferStatus status, Pageable pageable);

    Page<DocumentTransfer> findByRecipientIdAndTransferStatusOrderByCreatedDateDesc(Long recipientId, TransferStatus status, Pageable pageable);

    Page<DocumentTransfer> findBySenderIdAndSubjectContainingIgnoreCaseOrderByCreatedDateDesc(Long senderId, String search, Pageable pageable);

    Page<DocumentTransfer> findByRecipientIdAndSubjectContainingIgnoreCaseOrderByCreatedDateDesc(Long recipientId, String search, Pageable pageable);

    Optional<DocumentTransfer> findByIdAndSenderId(Long id, Long senderId);

    Optional<DocumentTransfer> findByIdAndRecipientId(Long id, Long recipientId);

    @Query("SELECT d FROM DocumentTransfer d WHERE d.recipientId = :userId OR d.recipientName LIKE %:name% ORDER BY d.createdDate DESC")
    Page<DocumentTransfer> findReceivedTransfers(@Param("userId") Long userId, @Param("name") String name, Pageable pageable);

    @Query("SELECT d FROM DocumentTransfer d WHERE (d.recipientId = :userId OR d.recipientName LIKE %:name%) AND d.transferStatus = :status ORDER BY d.createdDate DESC")
    Page<DocumentTransfer> findReceivedTransfersByStatus(@Param("userId") Long userId, @Param("name") String name, @Param("status") TransferStatus status, Pageable pageable);

    long countBySenderId(Long senderId);

    long countByRecipientId(Long recipientId);

    long countBySenderIdAndTransferStatus(Long senderId, TransferStatus status);

    long countByRecipientIdAndTransferStatus(Long recipientId, TransferStatus status);

    @Query("SELECT COALESCE(SUM(d.totalFiles), 0) FROM DocumentTransfer d WHERE d.senderId = :userId AND d.transferStatus = 'SENT'")
    long totalFilesSentByUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(d.totalSize), 0) FROM DocumentTransfer d WHERE d.senderId = :userId AND d.transferStatus = 'SENT'")
    long totalBytesSentByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(d) FROM DocumentTransfer d WHERE d.recipientId = :userId OR d.recipientName LIKE %:name%")
    long countReceivedTransfers(@Param("userId") Long userId, @Param("name") String name);

    @Query("SELECT COALESCE(SUM(d.totalFiles), 0) FROM DocumentTransfer d WHERE d.recipientId = :userId AND d.transferStatus = 'RECEIVED'")
    long totalFilesReceivedByUser(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(d.totalSize), 0) FROM DocumentTransfer d WHERE d.recipientId = :userId AND d.transferStatus = 'RECEIVED'")
    long totalBytesReceivedByUser(@Param("userId") Long userId);

    Optional<DocumentTransfer> findByReferenceNo(String referenceNo);
}
