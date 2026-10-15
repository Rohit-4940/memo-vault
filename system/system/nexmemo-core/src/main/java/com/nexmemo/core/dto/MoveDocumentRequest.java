package com.nexmemo.core.dto;

import lombok.Data;

/** Move a document into a folder (null = uncategorised). */
@Data
public class MoveDocumentRequest {
    private Long folderId;
}
