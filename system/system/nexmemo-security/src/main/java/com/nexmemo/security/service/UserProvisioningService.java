package com.nexmemo.security.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.dto.CreateUserRequest;
import com.nexmemo.security.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    public User createUser(CreateUserRequest request, Long adminId) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.getRoleId()));
        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(role);
        user.setStatus(Status.ACTIVE);
        user.setEmailVerified(false);
        user.setAccountLocked(false);
        user.setForcePasswordChange(true);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        String tokenValue = UUID.randomUUID().toString();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(tokenValue);
        token.setTokenType(EmailVerificationToken.TokenType.EMAIL_VERIFICATION);
        token.setExpiresAt(LocalDateTime.now().plusHours(24));
        tokenRepository.save(token);
        emailService.sendWelcomeEmail(user.getEmail(), user.getName(), tokenValue);
        log.info("User created by admin {}: {}", adminId, user.getUsername());
        return user;
    }
    public void setInitialPassword(String token, String newPassword) {
        EmailVerificationToken evt = tokenRepository.findByTokenAndIsUsedFalse(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token."));
        if (evt.isExpired()) {
            throw new IllegalArgumentException("This link has expired. Please contact your administrator.");
        }
        User user = evt.getUser();
        validatePasswordStrength(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        user.setForcePasswordChange(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        evt.setIsUsed(true);
        evt.setUsedAt(LocalDateTime.now());
    }
    private void validatePasswordStrength(String password) {
        if (password.length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        if (!password.matches(".*[A-Z].*"))
            throw new IllegalArgumentException("Password must contain at least one uppercase letter.");
        if (!password.matches(".*[0-9].*"))
            throw new IllegalArgumentException("Password must contain at least one digit.");
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?].*"))
            throw new IllegalArgumentException("Password must contain at least one special character.");
    }
}
