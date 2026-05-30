package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Entity
@Table(name = "email_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EmailConfig {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    /** Logical name — e.g. "primary", "backup" */
    @Column(name = "config_key", unique = true, nullable = false, length = 100)
    private String configKey;

    @Column(name = "smtp_host", nullable = false)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private Integer smtpPort = 587;

    @Column(name = "smtp_username", nullable = false)
    private String smtpUsername;

    /** Encrypted via Jasypt before persistence */
    @Column(name = "smtp_password_encrypted", nullable = false, length = 512)
    private String smtpPasswordEncrypted;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "from_name")
    private String fromName;

    @Column(name = "use_tls", nullable = false)
    private Boolean useTls = true;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "modified_by")
    private Long modifiedBy;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    @PrePersist
    void prePersist() { this.createdDate = LocalDateTime.now(); }

    @PreUpdate
    void preUpdate() { this.modifiedDate = LocalDateTime.now(); }
}
