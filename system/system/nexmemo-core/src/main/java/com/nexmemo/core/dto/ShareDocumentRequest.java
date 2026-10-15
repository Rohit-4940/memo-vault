package com.nexmemo.core.dto;

import lombok.*;

/**
 * Request to share a vault document into a conversation message (Enterprise Phase 5).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareDocumentRequest {

    /** Vault document to share. Required. */
    private Long documentId;

    /** Informational version snapshot; the link always follows the document's live access rules. */
    private Integer versionNo;

    /** Optional note shown on the card. */
    private String note;

    /**
     * When true (the default) the share also grants read access to the whole conversation. When
     * false the card is posted but each recipient can only preview it if they already had access.
     */
    private Boolean grantAccess;
}
