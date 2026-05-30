package com.nexmemo.security.dto;

import com.nexmemo.core.enums.RoleAccess;
import com.nexmemo.core.enums.RoleType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RoleDto {
    private Long id;
    @NotBlank
    private String roleName;
    private RoleType roleType;
    private RoleAccess roleAccess;
    private Boolean isRoleApprovable;
    private BigDecimal roleApprovalLimitAmount;
    private List<Long> menuIds;
}
