package com.nexmemo.security.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Data
public class ChangePasswordRequest {
    @NotBlank private String currentPassword;
    @NotBlank private String newPassword;
    @NotBlank private String confirmPassword;
}
