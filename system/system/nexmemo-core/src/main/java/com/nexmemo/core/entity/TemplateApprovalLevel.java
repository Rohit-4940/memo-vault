package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "template_approval_level")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class TemplateApprovalLevel extends BaseEntity<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private MemoTemplate template;

    @Column(name = "level_order", nullable = false)
    private Integer levelOrder;

    @Column(name = "approver_type", length = 20, nullable = false)
    private String approverType;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(name = "approver_name")
    private String approverName;
}
