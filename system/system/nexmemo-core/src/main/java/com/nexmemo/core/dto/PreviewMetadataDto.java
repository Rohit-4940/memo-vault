package com.nexmemo.core.dto;

import com.nexmemo.core.enums.PreviewKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tells the client how (or whether) a document can be rendered inline, without exposing bytes.
 * {@code contentType} is the canonical, safe type the server will serve — never the stored/declared
 * one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewMetadataDto {
    private Long documentId;
    private Integer versionNo;
    private String filename;
    private Long sizeBytes;
    private PreviewKind previewKind;
    private boolean previewable;
    private String contentType;
}
