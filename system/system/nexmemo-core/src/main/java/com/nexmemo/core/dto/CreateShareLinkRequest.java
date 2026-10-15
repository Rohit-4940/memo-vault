package com.nexmemo.core.dto;

import lombok.Data;

/** Request body for creating a share link. */
@Data
public class CreateShareLinkRequest {
    /** Hours until expiry; null = never expires (discouraged). */
    private Integer expiresInHours;
    /** Optional download cap; null = unlimited. */
    private Integer maxDownloads;
    /** Optional plaintext password, hashed before storage. */
    private String password;
}
