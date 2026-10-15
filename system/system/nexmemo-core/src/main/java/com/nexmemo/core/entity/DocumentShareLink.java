package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Time-boxed, revocable share link for a {@link DocumentRecord}.
 *
 * <p>The token is a high-entropy random string (never the document id). Access is granted only
 * while the link is not revoked, not expired, and under its download cap; every access is
 * re-validated server-side. Passwords, when set, are stored as a BCrypt hash, never in clear.</p>
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_share_link", indexes = {
        @Index(name = "idx_doclink_token", columnList = "token", unique = true),
        @Index(name = "idx_doclink_document", columnList = "document_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentShareLink extends BaseEntity<Long> {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @Column(name = "max_downloads")
    private Integer maxDownloads;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;
}
