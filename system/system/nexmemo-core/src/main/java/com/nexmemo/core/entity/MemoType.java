package com.nexmemo.core.entity;

import com.nexmemo.core.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "memo_type")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class MemoType extends BaseEntity<Long> implements Serializable {

    @NotBlank
    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    /**
     * Dynamic form definition as JSON: a list of {@link com.nexmemo.core.dto.FormField}s.
     * Drives the memo-creation form and required-field validation (Phase 4c).
     */
    @Column(name = "form_schema", columnDefinition = "TEXT")
    private String formSchema;

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
}
