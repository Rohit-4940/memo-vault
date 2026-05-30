package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Entity
@Table(name = "device_registry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class DeviceRegistry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Integer version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_fingerprint", nullable = false, length = 512)
    private String deviceFingerprint;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "is_trusted", nullable = false)
    private Boolean isTrusted = false;

    @Column(name = "trust_expires_at")
    private LocalDateTime trustExpiresAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void prePersist() { this.createdDate = LocalDateTime.now(); }

    public boolean isTrustValid() {
        return isTrusted && (trustExpiresAt == null || LocalDateTime.now().isBefore(trustExpiresAt));
    }
}
