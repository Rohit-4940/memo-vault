package com.nexmemo.core.entity;

import com.nexmemo.core.enums.DocumentClassification;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * A reference from a chat message to a canonical vault {@link DocumentRecord} (Enterprise Phase 5).
 *
 * <p>Unlike {@link ChatAttachment}, no bytes are copied: the message stores the document id plus a
 * small immutable snapshot (title/filename/type/size/classification) so a card can be rendered
 * later. Access is <b>never</b> implied by this row — the recipient's read capability is re-evaluated
 * against the document on every preview/download, so unsharing, deleting or re-classifying the
 * document immediately changes what a chat participant can see.</p>
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_message_document", indexes = {
        @Index(name = "idx_cmsgdoc_message", columnList = "message_id"),
        @Index(name = "idx_cmsgdoc_conv", columnList = "conversation_id"),
        @Index(name = "idx_cmsgdoc_document", columnList = "document_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatMessageDocument extends BaseEntity<Long> implements Serializable {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** Version number captured at share time (informational; the link is not version-pinned). */
    @Column(name = "document_version_no")
    private Integer documentVersionNo;

    @Column(name = "document_title", length = 500)
    private String documentTitle;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "sha256", length = 64, columnDefinition = "CHAR(64)")
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification", length = 30)
    private DocumentClassification classification;

    /** Optional free-text note from the sharer, shown on the card. */
    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "shared_by_id", nullable = false)
    private Long sharedById;

    /** True when the share also created a read grant for the conversation. */
    @Builder.Default
    @Column(name = "granted_read", nullable = false)
    private Boolean grantedRead = false;

    @Builder.Default
    @Column(name = "is_revoked", nullable = false)
    private Boolean isRevoked = false;
}
