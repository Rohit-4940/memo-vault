package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "memo_workflow_permission")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class MemoWorkflowPermission extends BaseEntity<Long> implements Serializable {

    @Column(name = "memo_type_id")
    private Long memoTypeId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "can_create", nullable = false)
    private Boolean canCreate = false;

    @Column(name = "can_edit", nullable = false)
    private Boolean canEdit = false;

    @Column(name = "can_approve", nullable = false)
    private Boolean canApprove = false;

    @Column(name = "can_reject", nullable = false)
    private Boolean canReject = false;

    @Column(name = "can_view", nullable = false)
    private Boolean canView = true;
}
