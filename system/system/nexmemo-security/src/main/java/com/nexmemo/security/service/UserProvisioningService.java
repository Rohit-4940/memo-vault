package com.nexmemo.security.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.DepartmentRepository;
import com.nexmemo.security.dto.CreateUserRequest;
import com.nexmemo.security.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;
    private static final SecureRandom RANDOM = new SecureRandom();

    public User createUser(CreateUserRequest request, Long adminId) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + request.getRoleId()));

        String plainPassword = request.getPassword() != null && !request.getPassword().isBlank()
                ? request.getPassword()
                : generatePassword();

        User user = new User();
        user.setName(request.getName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(plainPassword));
        user.setRole(role);
        user.setStatus(Status.ACTIVE);
        user.setEmailVerified(true);
        user.setAccountLocked(false);
        user.setForcePasswordChange(request.isForcePasswordChange());
        user.setFailedLoginAttempts(0);

        if (request.getBranchIds() != null && !request.getBranchIds().isEmpty()) {
            user.setBranches(branchRepository.findAllById(request.getBranchIds()));
        }

        userRepository.save(user);
        log.info("User created by admin {}: {} (password: {})", adminId, user.getUsername(),
                request.getPassword() != null ? "user-provided" : "auto-generated");

        if (request.isSendEmail()) {
            emailService.sendAccountCreatedEmail(user.getEmail(), user.getName(), user.getUsername(), plainPassword);
        }

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

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        sb.append(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        sb.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        for (int i = 0; i < 8; i++) {
            sb.append(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }
        return sb.toString();
    }
}
