package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/

@Entity
@Table(name = "login_attempt", indexes = {
        @Index(name = "idx_la_username_created", columnList = "username,created_date"),
        @Index(name = "idx_la_ip_created", columnList = "ip_address,created_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "is_success", nullable = false)
    private Boolean isSuccess = false;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void prePersist() { this.createdDate = LocalDateTime.now(); }
}
