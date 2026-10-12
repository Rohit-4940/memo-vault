package com.nexmemo.core.entity;

import com.nexmemo.core.enums.ChatAttachmentScanStatus;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

/**
 * A file attached to a chat message (Phase 2f).
 *
 * SECURITY: raw bytes live in the database ({@code file_data} VARBINARY(MAX)) and are only ever
 * streamed out through the authorized download endpoint. {@code sha256} is a content hash for
 * duplication/quarantine checks, and {@code scanStatus} gates release: anything other than
 * {@code CLEAN} is not downloadable.
 *
 * @author : NexMemo
 **/
@Entity
@Table(name = "chat_attachment", indexes = {
        @Index(name = "idx_cattach_message", columnList = "message_id"),
        @Index(name = "idx_cattach_conv", columnList = "conversation_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ChatAttachment extends BaseEntity<Long> implements Serializable {

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sha256;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 20)
    private ChatAttachmentScanStatus scanStatus = ChatAttachmentScanStatus.PENDING;

    @Lob
    @Column(name = "file_data", columnDefinition = "VARBINARY(MAX)")
    private byte[] fileData;

    @Column(name = "uploaded_by_id", nullable = false)
    private Long uploadedById;
}