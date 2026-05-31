package com.nexmemo.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateUserRequest {
    @NotBlank private String name;
    @NotBlank private String username;
    @Email @NotBlank private String email;
    private String password;
    private String contactNo;
    private String address;
    @NotNull  private Long roleId;
    private List<Long> branchIds;
    private boolean sendEmail = true;
    private boolean forcePasswordChange = false;
}
