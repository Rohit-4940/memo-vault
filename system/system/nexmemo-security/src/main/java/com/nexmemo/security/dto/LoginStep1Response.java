package com.nexmemo.security.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Data
@Builder
public class LoginStep1Response {
    private boolean requiresOtp;
    private Long userId;
    private String username;
    private String role;
    private Long roleId;
    private Boolean forcePasswordChange;
    private String accessToken;
    private String refreshToken;
    private String message;
}
