package com.nexmemo.core.dto;

import com.nexmemo.core.enums.PreviewKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Public landing metadata for a share link (Enterprise Phase 4). Deliberately exposes only what the
 * visitor needs to decide whether to enter a password / download — never the document id, storage
 * key, owner or classification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareLinkLandingDto {
    private String filename;
    private Long sizeBytes;
    private String contentType;
    private PreviewKind previewKind;
    private boolean previewable;
    private boolean passwordRequired;
    private LocalDateTime expiresAt;
    /** Remaining downloads, or null when unlimited. */
    private Integer remainingDownloads;
}
