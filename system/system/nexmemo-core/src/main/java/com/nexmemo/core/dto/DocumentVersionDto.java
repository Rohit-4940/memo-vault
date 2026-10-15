package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One immutable revision of a document. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionDto {
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String notes;
    private Long uploadedById;
    private LocalDateTime createdDate;
}
