package com.nexmemo.security.service;

import com.nexmemo.core.dto.SecurityProperties;
import com.nexmemo.core.entity.OtpVerification;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public void generateAndSendOtp(User user, OtpVerification.OtpType type, String ip) {
        otpRepository.invalidatePreviousOtps(user.getId(), type.name());

        String code = securityProperties.getOtp().isSkipInDev()
                ? securityProperties.getOtp().getFixedDevOtp()
                : generateCode();

        OtpVerification otp = new OtpVerification();
        otp.setUser(user);
        otp.setOtpCode(code);
        otp.setOtpType(type);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(securityProperties.getOtp().getExpiryMinutes()));
        otp.setIpAddress(ip);
        otpRepository.save(otp);
        emailService.sendOtpEmail(user.getEmail(), user.getName(), code,
                securityProperties.getOtp().getExpiryMinutes());
    }
    public void verifyOtp(User user, String code, OtpVerification.OtpType type, String ip) {
        OtpVerification otp = otpRepository.findLatestActiveOtp(user.getId(), type.name())
                .orElseThrow(() -> new IllegalArgumentException("No active OTP found. Please request a new one."));

        if (otp.isExpired()) {
            throw new IllegalArgumentException("OTP has expired. Please request a new one.");
        }
        otp.setAttemptCount(otp.getAttemptCount() + 1);
        if (otp.getAttemptCount() > securityProperties.getOtp().getMaxAttempts()) {
            otp.setIsUsed(true);
            throw new IllegalArgumentException("Too many OTP attempts. Please request a new code.");
        }
        if (!otp.getOtpCode().equals(code)) {
            throw new IllegalArgumentException("Invalid OTP code.");
        }
        otp.setIsUsed(true);
        otp.setVerifiedAt(LocalDateTime.now());
    }
    private String generateCode() {
        int length = securityProperties.getOtp().getLength();
        int max = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", secureRandom.nextInt(max));
    }
}
