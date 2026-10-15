package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for a vault document shared into a chat message (Enterprise Phase 5).
 *
 * <p>SECURITY: metadata is only populated when the viewer currently has read capability on the
 * document. Otherwise {@code restricted} is true and {@code documentId} plus every metadata field
 * are null, so an unauthorized recipient learns nothing beyond the fact that a document was shared.</p>
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDocumentDto {

    private Long id;
    private Long messageId;
    private Long conversationId;

    /** Null when the viewer is not permitted to read the document. */
    private Long documentId;
    private Integer documentVersionNo;

    private String title;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private String classification;
    private String sha256;

    private String note;
    private Long sharedById;

    /** True when the share granted read access to the whole conversation. */
    private Boolean grantedRead;
    private Boolean isRevoked;

    /** True when the viewer cannot (currently) read the document. */
    private Boolean restricted;
    /** True when the viewer may revoke the share (sharer or administrator). */
    private Boolean canRevoke;

    private LocalDateTime createdDate;
}
