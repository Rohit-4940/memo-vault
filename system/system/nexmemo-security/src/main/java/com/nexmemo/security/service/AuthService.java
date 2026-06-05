package com.nexmemo.security.service;

import com.nexmemo.core.dto.SecurityProperties;
import com.nexmemo.core.entity.*;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.dto.*;
import com.nexmemo.security.repository.*;
import com.nexmemo.security.util.DeviceUtil;
import com.nexmemo.security.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpRepository otpRepository;
    private final DeviceRegistryRepository deviceRegistryRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuditLogRepository auditLogRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final DeviceUtil deviceUtil;
    private final SecurityProperties securityProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserProvisioningService provisioningService;

    public LoginStep1Response initiateLogin(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = deviceUtil.extractIp(httpRequest);
        String fingerprint = deviceUtil.buildFingerprint(httpRequest);

        int recentFailures = loginAttemptRepository.countRecentFailures(
                request.getUsername(), ip,
                LocalDateTime.now().minusMinutes(securityProperties.getBruteForce().getAttemptWindowMinutes())
        );
        if (recentFailures >= securityProperties.getBruteForce().getMaxAttempts()) {
            recordAudit("LOGIN_BLOCKED", null, null, ip, httpRequest, "Brute force lockout", AuditLog.AuditStatus.SUSPICIOUS);
            throw new LockedException("Account temporarily locked due to too many failed attempts.");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    recordFailedAttempt(request.getUsername(), ip, "USER_NOT_FOUND");
                    return new BadCredentialsException("Invalid credentials");
                });

        if (user.getAccountLocked()) {
            throw new LockedException("Your account is locked. Contact an administrator.");
        }
        if (user.getStatus() != Status.ACTIVE) {
            throw new IllegalStateException("Account is not active.");
        }
        if (!user.getEmailVerified()) {
            throw new IllegalStateException("Please verify your email before logging in.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailedAttempt(request.getUsername(), ip, "BAD_PASSWORD");
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            if (user.getFailedLoginAttempts() >= securityProperties.getBruteForce().getMaxAttempts()) {
                user.setAccountLocked(true);
                user.setLockedUntil(LocalDateTime.now()
                        .plusMinutes(securityProperties.getBruteForce().getLockoutDurationMinutes()));
                emailService.sendAccountLockedNotification(user.getEmail(), user.getName(),
                        securityProperties.getBruteForce().getLockoutDurationMinutes());
                recordAudit("ACCOUNT_LOCKED", "USER", user.getId(),
                        user.getId(), user.getUsername(), ip, httpRequest,
                        "Account locked due to max failed attempts", AuditLog.AuditStatus.SUSPICIOUS);
            }
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);

        if (user.getPasswordChangedAt() != null) {
            LocalDateTime expiryDate = user.getPasswordChangedAt()
                    .plusDays(securityProperties.getPassword().getPasswordExpiryDays());
            if (LocalDateTime.now().isAfter(expiryDate)) {
                user.setForcePasswordChange(true);
            }
        }

        boolean deviceTrusted = deviceRegistryRepository
                .findByUserAndDeviceFingerprint(user, fingerprint)
                .map(DeviceRegistry::isTrustValid)
                .orElse(false);

        boolean skipOtp = (securityProperties.getOtp().isSkipInDev() || deviceTrusted);

        if (!skipOtp) {
            otpService.generateAndSendOtp(user, OtpVerification.OtpType.LOGIN, ip);
            recordAudit("OTP_SENT", "USER", user.getId(),
                    user.getId(), user.getUsername(), ip, httpRequest,
                    "OTP sent for login", AuditLog.AuditStatus.SUCCESS);
            return LoginStep1Response.builder()
                    .requiresOtp(true)
                    .message("OTP sent to your registered email.")
                    .userId(user.getId())
                    .build();
        }

        return completeLogin(user, fingerprint, httpRequest, null);
    }

    public LoginStep1Response verifyOtp(OtpVerifyRequest request, HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BadCredentialsException("Invalid session"));
        otpService.verifyOtp(user, request.getOtpCode(), OtpVerification.OtpType.LOGIN,
                deviceUtil.extractIp(httpRequest));
        String fingerprint = deviceUtil.buildFingerprint(httpRequest);
        if (Boolean.TRUE.equals(request.getTrustDevice())) {
            trustDevice(user, fingerprint, httpRequest);
        }
        return completeLogin(user, fingerprint, httpRequest, httpResponse);
    }

    private LoginStep1Response completeLogin(User user, String fingerprint,
                                             HttpServletRequest httpRequest,
                                             HttpServletResponse httpResponse) {
        String tokenFamily = UUID.randomUUID().toString().substring(0, 8);
        String accessToken = jwtUtil.generateAccessTokenWithFamily(user.getId(), user.getUsername(),
                user.getRole().getRoleName(), tokenFamily);
        String refreshToken = jwtUtil.generateRefreshTokenWithFamily(user.getId(), user.getUsername(), user.getRole().getRoleName(), tokenFamily);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(sha256(refreshToken));
        rt.setDeviceFingerprint(fingerprint);
        rt.setIpAddress(deviceUtil.extractIp(httpRequest));
        rt.setUserAgent(deviceUtil.extractUserAgent(httpRequest));
        rt.setExpiresAt(LocalDateTime.now().plusDays(securityProperties.getJwt().getRefreshTokenExpiryDays()));
        refreshTokenRepository.save(rt);

        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(deviceUtil.extractIp(httpRequest));
        user.setLastLoginMac(deviceUtil.extractMacAddress(httpRequest));

        recordAudit("LOGIN_SUCCESS", "USER", user.getId(),
                user.getId(), user.getUsername(), deviceUtil.extractIp(httpRequest),
                httpRequest, "Login via " + fingerprint, AuditLog.AuditStatus.SUCCESS);

        if (httpResponse != null) {
            setTokenCookies(httpResponse, accessToken, refreshToken);
        }

        return LoginStep1Response.builder()
                .requiresOtp(false)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().getRoleName())
                .roleId(user.getRole().getId())
                .roleType(user.getRole().getRoleType().name())
                .roleAccess(user.getRole().getRoleAccess().name())
                .forcePasswordChange(user.getForcePasswordChange())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .message("Login successful")
                .build();
    }

    public LoginStep1Response refreshToken(String refreshTokenValue, HttpServletRequest req,
                                           HttpServletResponse res) {
        String ip = deviceUtil.extractIp(req);
        String fingerprint = deviceUtil.buildFingerprint(req);

        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new BadCredentialsException("Refresh token is required");
        }

        if (!jwtUtil.isTokenValid(refreshTokenValue)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String tokenType = jwtUtil.extractTokenType(refreshTokenValue);
        if (!"REFRESH".equals(tokenType)) {
            throw new BadCredentialsException("Invalid token type");
        }

        String tokenHash = sha256(refreshTokenValue);

        if (tokenBlacklistService.isRefreshTokenBlacklisted(tokenHash)) {
            String jti = jwtUtil.extractJti(refreshTokenValue);
            try {
                Long userId = jwtUtil.extractUserId(refreshTokenValue);
                refreshTokenRepository.revokeAllActiveByUserId(userId);
                recordAudit("TOKEN_REUSE_DETECTED", "USER", userId, ip, req,
                        "Refresh token reuse detected — all tokens revoked", AuditLog.AuditStatus.SUSPICIOUS);
                clearCookies(res);
            } catch (Exception e) {
                log.warn("Failed to revoke tokens on reuse: {}", e.getMessage());
            }
            throw new BadCredentialsException("Token has been revoked");
        }

        RefreshToken storedToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found"));

        if (!storedToken.isValid()) {
            refreshTokenRepository.revokeAllActiveByUserId(storedToken.getUser().getId());
            tokenBlacklistService.blacklistRefreshToken(tokenHash,
                    securityProperties.getJwt().getRefreshTokenExpiryDays() * 86400L);
            User staleUser = storedToken.getUser();
            recordAudit("TOKEN_REUSE_DETECTED", "USER", staleUser.getId(),
                    staleUser.getId(), staleUser.getUsername(), ip, req,
                    "Stale refresh token used — all tokens revoked", AuditLog.AuditStatus.SUSPICIOUS);
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        if (!storedToken.getDeviceFingerprint().equals(fingerprint)) {
            User deviceUser = storedToken.getUser();
            recordAudit("DEVICE_MISMATCH_REFRESH", "USER", deviceUser.getId(),
                    deviceUser.getId(), deviceUser.getUsername(), ip, req,
                    "Device fingerprint mismatch during token refresh", AuditLog.AuditStatus.SUSPICIOUS);
        }

        User user = storedToken.getUser();

        storedToken.setIsRevoked(true);
        refreshTokenRepository.updateLastUsed(tokenHash, LocalDateTime.now());

        tokenBlacklistService.blacklistRefreshToken(tokenHash,
                securityProperties.getJwt().getRefreshTokenExpiryDays() * 86400L);

        String jti = jwtUtil.extractJti(refreshTokenValue);
        if (jti != null) {
            tokenBlacklistService.blacklistAccessToken(jti,
                    securityProperties.getJwt().getAccessTokenExpiryMinutes() * 60L);
        }

        String tokenFamily = jwtUtil.extractTokenFamily(refreshTokenValue);
        if (tokenFamily == null) {
            tokenFamily = UUID.randomUUID().toString().substring(0, 8);
        }

        String newAccessToken = jwtUtil.generateAccessTokenWithFamily(user.getId(), user.getUsername(),
                user.getRole().getRoleName(), tokenFamily);
        String newRefreshToken = jwtUtil.generateRefreshTokenWithFamily(user.getId(), user.getUsername(), user.getRole().getRoleName(), tokenFamily);

        RefreshToken newRt = new RefreshToken();
        newRt.setUser(user);
        newRt.setTokenHash(sha256(newRefreshToken));
        newRt.setDeviceFingerprint(fingerprint);
        newRt.setIpAddress(ip);
        newRt.setUserAgent(deviceUtil.extractUserAgent(req));
        newRt.setExpiresAt(LocalDateTime.now().plusDays(securityProperties.getJwt().getRefreshTokenExpiryDays()));
        refreshTokenRepository.save(newRt);

        setTokenCookies(res, newAccessToken, newRefreshToken);

        recordAudit("TOKEN_REFRESHED", "USER", user.getId(),
                user.getId(), user.getUsername(), ip, req, null, AuditLog.AuditStatus.SUCCESS);

        return LoginStep1Response.builder()
                .requiresOtp(false)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole().getRoleName())
                .roleId(user.getRole().getId())
                .roleType(user.getRole().getRoleType().name())
                .roleAccess(user.getRole().getRoleAccess().name())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .message("Token refreshed successfully")
                .build();
    }

    public void changePassword(Long userId, ChangePasswordRequest request, HttpServletRequest httpRequest) {
        String ip = deviceUtil.extractIp(httpRequest);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            loginAttemptRepository.save(LoginAttempt.builder()
                    .username(user.getUsername())
                    .ipAddress(ip)
                    .isSuccess(false)
                    .failureReason("INCORRECT_CURRENT_PASSWORD")
                    .build());
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        validatePasswordStrength(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setForcePasswordChange(false);

        refreshTokenRepository.revokeAllActiveByUserId(userId);

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String jti = jwtUtil.extractJti(token);
            if (jti != null) {
                tokenBlacklistService.blacklistAccessToken(jti,
                        securityProperties.getJwt().getAccessTokenExpiryMinutes() * 60L);
            }
        }

        emailService.sendPasswordChangedNotification(user.getEmail(), user.getName(), ip);

        recordAudit("PASSWORD_CHANGED", "USER", userId,
                userId, user.getUsername(), ip, httpRequest,
                "Password changed successfully", AuditLog.AuditStatus.SUCCESS);
    }

    public void forgotPassword(String email, HttpServletRequest httpRequest) {
        String ip = deviceUtil.extractIp(httpRequest);

        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getStatus() != Status.ACTIVE) {
                log.warn("Password reset requested for inactive user: {}", email);
                return;
            }

            String resetToken = UUID.randomUUID().toString();
            user.setResetPasswordToken(sha256(resetToken));
            user.setResetPasswordTokenExpiry(
                    LocalDateTime.now().plusHours(securityProperties.getPassword().getResetTokenExpiryHours()));
            userRepository.save(user);

            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetToken);

            recordAudit("PASSWORD_RESET_REQUESTED", "USER", user.getId(),
                    user.getId(), user.getUsername(), ip, httpRequest,
                    "Password reset link sent", AuditLog.AuditStatus.SUCCESS);
        } else {
            log.info("Password reset requested for unknown email: {}", email);
        }
    }

    public void resetPassword(String token, String newPassword, String confirmPassword,
                              HttpServletRequest httpRequest) {
        String ip = deviceUtil.extractIp(httpRequest);

        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        String hashedToken = sha256(token);
        User targetUser = userRepository.findByResetPasswordToken(hashedToken)
                .filter(u -> u.getResetPasswordTokenExpiry() != null &&
                        LocalDateTime.now().isBefore(u.getResetPasswordTokenExpiry()))
                .orElse(null);

        if (targetUser == null) {
            recordAudit("PASSWORD_RESET_FAILED", null, null, ip, httpRequest,
                    "Invalid or expired reset token", AuditLog.AuditStatus.FAILURE);
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        validatePasswordStrength(newPassword);

        targetUser.setPassword(passwordEncoder.encode(newPassword));
        targetUser.setPasswordChangedAt(LocalDateTime.now());
        targetUser.setResetPasswordToken(null);
        targetUser.setResetPasswordTokenExpiry(null);
        targetUser.setForcePasswordChange(false);

        refreshTokenRepository.revokeAllActiveByUserId(targetUser.getId());

        emailService.sendPasswordChangedNotification(targetUser.getEmail(), targetUser.getName(), ip);

        recordAudit("PASSWORD_RESET_COMPLETED", "USER", targetUser.getId(),
                targetUser.getId(), targetUser.getUsername(), ip, httpRequest,
                "Password reset completed", AuditLog.AuditStatus.SUCCESS);
    }

    public void logout(Long userId, HttpServletRequest req, HttpServletResponse res) {
        String fingerprint = deviceUtil.buildFingerprint(req);
        refreshTokenRepository.revokeByUserIdAndFingerprint(userId, fingerprint);

        String authHeader = req.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String jti = jwtUtil.extractJti(token);
            if (jti != null) {
                tokenBlacklistService.blacklistAccessToken(jti,
                        securityProperties.getJwt().getAccessTokenExpiryMinutes() * 60L);
            }
        }

        clearCookies(res);
        userRepository.findById(userId).ifPresent(u ->
            recordAudit("LOGOUT", "USER", userId,
                    u.getId(), u.getUsername(), deviceUtil.extractIp(req), req,
                    null, AuditLog.AuditStatus.SUCCESS)
        );
    }

    private void setTokenCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        Cookie access = new Cookie("access_token", accessToken);
        access.setHttpOnly(true);
        access.setSecure(true);
        access.setPath("/");
        access.setMaxAge(securityProperties.getJwt().getAccessTokenExpiryMinutes() * 60);
        access.setAttribute("SameSite", "Strict");

        Cookie refresh = new Cookie("refresh_token", refreshToken);
        refresh.setHttpOnly(true);
        refresh.setSecure(true);
        refresh.setPath("/api/v1/auth/refresh");
        refresh.setMaxAge(securityProperties.getJwt().getRefreshTokenExpiryDays() * 86400);
        refresh.setAttribute("SameSite", "Strict");

        response.addCookie(access);
        response.addCookie(refresh);
    }

    private void clearCookies(HttpServletResponse res) {
        Cookie access = new Cookie("access_token", "");
        access.setMaxAge(0); access.setPath("/"); access.setHttpOnly(true); access.setSecure(true);
        Cookie refresh = new Cookie("refresh_token", "");
        refresh.setMaxAge(0); refresh.setPath("/api/v1/auth/refresh"); refresh.setHttpOnly(true); refresh.setSecure(true);
        res.addCookie(access);
        res.addCookie(refresh);
    }

    private void trustDevice(User user, String fingerprint, HttpServletRequest req) {
        DeviceRegistry device = deviceRegistryRepository
                .findByUserAndDeviceFingerprint(user, fingerprint)
                .orElse(new DeviceRegistry());
        device.setUser(user);
        device.setDeviceFingerprint(fingerprint);
        device.setIpAddress(deviceUtil.extractIp(req));
        device.setUserAgent(deviceUtil.extractUserAgent(req));
        device.setIsTrusted(true);
        device.setTrustExpiresAt(LocalDateTime.now().plusDays(securityProperties.getDevice().getTrustDurationDays()));
        device.setLastSeenAt(LocalDateTime.now());
        deviceRegistryRepository.save(device);
    }

    private void recordFailedAttempt(String username, String ip, String reason) {
        LoginAttempt attempt = LoginAttempt.builder()
                .username(username)
                .ipAddress(ip)
                .isSuccess(false)
                .failureReason(reason)
                .build();
        loginAttemptRepository.save(attempt);
    }

    private void recordAudit(String action, String entityType, Long entityId, String ip,
                             HttpServletRequest req, String details, AuditLog.AuditStatus status) {
        recordAudit(action, entityType, entityId, null, null, ip, req, details, status);
    }

    private void recordAudit(String action, String entityType, Long entityId,
                             Long performedById, String performedByUsername, String ip,
                             HttpServletRequest req, String details, AuditLog.AuditStatus status) {
        AuditLog logEntry = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedById(performedById)
                .performedByUsername(performedByUsername)
                .ipAddress(ip)
                .userAgent(deviceUtil.extractUserAgent(req))
                .deviceFingerprint(deviceUtil.buildFingerprint(req))
                .details(details)
                .status(status)
                .build();
        auditLogRepository.save(logEntry);
    }

    private void validatePasswordStrength(String password) {
        var pwdConfig = securityProperties.getPassword();
        if (password.length() < pwdConfig.getMinLength()) {
            throw new IllegalArgumentException("Password must be at least " + pwdConfig.getMinLength() + " characters.");
        }
        if (pwdConfig.isRequireUppercase() && !password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        }
        if (pwdConfig.isRequireDigit() && !password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one digit.");
        }
        if (pwdConfig.isRequireSpecial() && !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*")) {
            throw new IllegalArgumentException("Password must contain at least one special character.");
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
