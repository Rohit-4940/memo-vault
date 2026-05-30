package com.nexmemo.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Data
public class OtpVerifyRequest {
    @NotNull  private Long userId;
    @NotBlank private String otpCode;
    private Boolean trustDevice = false;
}
