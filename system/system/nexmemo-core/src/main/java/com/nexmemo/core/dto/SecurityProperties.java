package com.nexmemo.core.dto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "nexmemo.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private BruteForce bruteForce = new BruteForce();
    private Device device = new Device();
    private Password password = new Password();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret;
        private int accessTokenExpiryMinutes = 15;
        private int refreshTokenExpiryDays = 7;
        private String issuer = "nexmemo";
    }

    @Data
    public static class Otp {
        private int expiryMinutes = 5;
        private int length = 6;
        private int maxAttempts = 3;
        private int lockoutMinutes = 15;
        private boolean skipInDev = false;
        private String fixedDevOtp = "123456";
    }

    @Data
    public static class BruteForce {
        private int maxAttempts = 5;
        private int lockoutDurationMinutes = 30;
        private int attemptWindowMinutes = 10;
    }

    @Data
    public static class Device {
        private int trustDurationDays = 30;
    }

    @Data
    public static class Password {
        private int resetTokenExpiryHours = 24;
        private boolean forceChangeOnFirstLogin = true;
        private int minLength = 8;
        private boolean requireUppercase = true;
        private boolean requireDigit = true;
        private boolean requireSpecial = true;
        private int passwordExpiryDays = 30;
        private int sessionTimeoutMinutes = 10;
    }

    @Data
    public static class RateLimit {
        private boolean redisEnabled = false;
        private int loginMaxPerMinute = 5;
        private int otpMaxPer5Minutes = 3;
        private int apiMaxPerMinute = 200;
        private int passwordResetMaxPerHour = 3;
    }
}
