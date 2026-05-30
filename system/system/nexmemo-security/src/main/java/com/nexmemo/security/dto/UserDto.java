package com.nexmemo.security.dto;

import com.nexmemo.core.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class UserDto {
    private Long id;
    @NotBlank
    private String name;
    @NotBlank
    private String username;
    @Email
    @NotBlank
    private String email;
    private Status status;
    private Long roleId;
    private String roleName;
    private List<Long> branchIds;
    private boolean forcePasswordChange;
}
