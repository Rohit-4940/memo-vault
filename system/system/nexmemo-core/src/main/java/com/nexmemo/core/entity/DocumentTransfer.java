package com.nexmemo.core.entity;

import com.nexmemo.core.enums.TransferPriority;
import com.nexmemo.core.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_transfer", indexes = {
    @Index(name = "idx_dt_ref_no", columnList = "reference_no"),
    @Index(name = "idx_dt_sender", columnList = "sender_id"),
    @Index(name = "idx_dt_recipient", columnList = "recipient_id"),
    @Index(name = "idx_dt_status", columnList = "transfer_status"),
    @Index(name = "idx_dt_created_date", columnList = "created_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DocumentTransfer extends BaseEntity<Long> {

    @Column(name = "reference_no", nullable = false, unique = true, length = 50)
    private String referenceNo;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "sender_name", nullable = false, length = 255)
    private String senderName;

    @Column(name = "sender_branch_id")
    private Long senderBranchId;

    @Column(name = "sender_branch_name", length = 255)
    private String senderBranchName;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_branch_id")
    private Long recipientBranchId;

    @Column(name = "recipient_branch_name", length = 255)
    private String recipientBranchName;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "transfer_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransferStatus transferStatus = TransferStatus.DRAFT;

    @Column(name = "priority", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransferPriority priority = TransferPriority.MEDIUM;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "total_size", nullable = false)
    private long totalSize;

    @Column(name = "sent_date")
    private LocalDateTime sentDate;

    @Column(name = "received_date")
    private LocalDateTime receivedDate;

    @Column(name = "archived_date")
    private LocalDateTime archivedDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
}
