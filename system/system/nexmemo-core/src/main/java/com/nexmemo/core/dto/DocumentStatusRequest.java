package com.nexmemo.core.dto;

import com.nexmemo.core.enums.DocumentStatus;
import lombok.Data;

/** Change a document's lifecycle status (ACTIVE / ARCHIVED / LEGAL_HOLD). */
@Data
public class DocumentStatusRequest {
    private DocumentStatus status;
    /** Optional explanation recorded in the audit trail (e.g. the legal matter reference). */
    private String reason;
}
