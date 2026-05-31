package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "category_document_requirement")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class CategoryDocumentRequirement extends BaseEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private MemoCategory category;

    @Column(name = "document_name", nullable = false)
    private String documentName;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;
}
