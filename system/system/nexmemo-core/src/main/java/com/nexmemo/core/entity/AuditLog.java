package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class AuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "performed_by_id")
    private Long performedById;

    @Column(name = "performed_by_username", length = 255)
    private String performedByUsername;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_fingerprint", length = 512)
    private String deviceFingerprint;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private AuditStatus status = AuditStatus.SUCCESS;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void prePersist() { this.createdDate = LocalDateTime.now(); }

    public enum AuditStatus { SUCCESS, FAILURE, SUSPICIOUS }
}
