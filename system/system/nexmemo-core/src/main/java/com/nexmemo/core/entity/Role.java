package com.nexmemo.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.nexmemo.core.enums.RoleAccess;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.core.enums.Status;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author : Rohit Chapagain
 * @created : 2026-04-03, Friday
 **/
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Role extends BaseEntity<Long> implements Serializable {

    // REMOVED: Long id (Already in BaseEntity)
    // REMOVED: int version (Already in BaseEntity)
    // REMOVED: createdByName/modifiedByName (Handled by Auditing/DTOs)

    @NotBlank
    @Column(name = "role_name", unique = true, nullable = false)
    @EqualsAndHashCode.Include
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleType roleType = RoleType.APPROVAL;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoleAccess roleAccess = RoleAccess.OWN;

    @Column(nullable = false)
    private Boolean isRoleApprovable = false;

    @Column(precision = 19, scale = 4) // Better for financial/amount values
    private BigDecimal roleApprovalLimitAmount;

    /**
     * Helper for Security check: ROLE_ADMIN, ROLE_USER, etc.
     */
    public String getAuthorityName() {
        return "ROLE_" + this.roleName.toUpperCase();
    }
}
