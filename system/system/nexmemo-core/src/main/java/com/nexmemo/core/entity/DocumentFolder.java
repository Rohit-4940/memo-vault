package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A personal, owner-scoped organisational folder for the document vault (Enterprise Phase 4).
 *
 * <p>Folders are an <b>organisational overlay only</b>: moving a document into a folder never
 * changes who may read it. Authorization is still resolved per document through
 * {@code DocumentAuthorizationService}; a folder is visible only to its owner (and administrators),
 * so a folder tree can never leak the existence of documents the caller cannot read.</p>
 *
 * <p>{@code path} is a materialised ancestor chain ({@code /12/48/}) used to reject cycles when a
 * folder is re-parented.</p>
 *
 * @author : NexMemo
 */
@Entity
@Table(name = "document_folder", indexes = {
        @Index(name = "idx_docfolder_owner", columnList = "owner_id,is_deleted"),
        @Index(name = "idx_docfolder_parent", columnList = "parent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFolder extends BaseEntity<Long> {

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Null for a root folder. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Materialised ancestor chain, e.g. {@code /12/48/}. */
    @Column(name = "path", nullable = false, length = 1000)
    private String path = "/";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
