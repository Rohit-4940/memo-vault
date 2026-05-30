package com.nexmemo.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Data
public class CreateUserRequest {
    @NotBlank private String name;
    @NotBlank private String username;
    @Email @NotBlank private String email;
    @NotNull  private Long roleId;
    private java.util.List<Long> branchIds;
}
