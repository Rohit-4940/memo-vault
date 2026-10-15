package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A share link as returned to a permitted user. The raw token is echoed only on creation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentShareLinkDto {
    private Long id;
    private Long documentId;
    private String token;
    private String shareUrl;
    private LocalDateTime expiresAt;
    private Boolean revoked;
    private Integer maxDownloads;
    private Integer downloadCount;
    private Boolean passwordProtected;
    private Long createdById;
    private LocalDateTime createdDate;
}
