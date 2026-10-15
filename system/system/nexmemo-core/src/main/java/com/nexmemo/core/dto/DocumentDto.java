package com.nexmemo.core.dto;

import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentSourceType;
import com.nexmemo.core.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** Metadata view of a canonical document. Never exposes storage paths. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {
    private Long id;
    private String title;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String description;
    private Long ownerId;
    private DocumentClassification classification;
    private DocumentStatus status;
    private Long branchId;
    private Long departmentId;
    private Integer currentVersion;
    private DocumentSourceType sourceType;
    private Long sourceRefId;
    /** Organisational folder (null = uncategorised). Never affects read permission. */
    private Long folderId;
    private LocalDateTime retentionUntil;
    /** Explanation recorded when the document was placed under legal hold. */
    private String holdReason;
    private List<String> tags;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
}
