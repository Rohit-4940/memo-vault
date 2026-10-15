package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Free-text tag on a {@link DocumentRecord}, used for catalogue filtering.
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_tag", indexes = {
        @Index(name = "idx_doctag_document", columnList = "document_id"),
        @Index(name = "idx_doctag_tag", columnList = "tag")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_doctag_doc_tag", columnNames = {"document_id", "tag"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTag extends BaseEntity<Long> {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "tag", nullable = false, length = 80)
    private String tag;
}
