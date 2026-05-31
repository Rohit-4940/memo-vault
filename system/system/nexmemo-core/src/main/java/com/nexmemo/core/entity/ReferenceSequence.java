package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "reference_sequence", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"fiscal_year_id", "scope_type", "scope_id", "sequence_type"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ReferenceSequence extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_year_id", nullable = false)
    private FiscalYear fiscalYear;

    @Column(name = "scope_type", length = 20, nullable = false)
    private String scopeType;

    @Column(name = "scope_id", nullable = false)
    private Long scopeId = 0L;

    @Column(name = "sequence_type", length = 30, nullable = false)
    private String sequenceType;

    @Column(name = "prefix", length = 20)
    private String prefix;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence = 0L;

    @Column(name = "suffix", length = 20)
    private String suffix;
}
