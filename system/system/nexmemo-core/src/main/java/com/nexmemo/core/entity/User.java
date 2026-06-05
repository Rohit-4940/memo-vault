package com.nexmemo.core.entity;

/**
 * @author : Rohit Chapagain
 * @created : 2026-04-03, Friday
 **/

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexmemo.core.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_username", columnList = "user_name"),
        @Index(name = "idx_user_email",    columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class User extends BaseEntity<Long> implements UserDetails {

    // ── Basic identity ────────────────────────────────────────────────────────

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Username is required")
    @Column(name = "user_name", unique = true, nullable = false)
    @EqualsAndHashCode.Include
    private String username;

    @Email(message = "Invalid email")
    @Column(unique = true, nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    // ── Status & role ─────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.ACTIVE;

    /**
     * EAGER load — role is always needed for authority/permission checks.
     * Do NOT change to LAZY unless you update JwtAuthenticationFilter accordingly.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    // ── Branch assignments ────────────────────────────────────────────────────

    @JsonIgnore
    @BatchSize(size = 20)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_branches",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id")
    )
    private List<Branch> branches = new ArrayList<>();

    // ── Security / audit fields ───────────────────────────────────────────────

    /** Set to true after user clicks the welcome-email set-password link */
    @Column(name = "email_verified", nullable = false)
    private Boolean emailVerified = false;

    /**
     * Set to true by system after max failed login attempts.
     * Admin can manually unlock. Auto-unlocks when lockedUntil passes.
     */
    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    /** Rolling counter — reset to 0 on successful login */
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    /**
     * When the temporary lockout expires.
     * Null = permanent until admin unlocks.
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Timestamp of the last successful login */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** IP address of last login */
    @Column(name = "last_login_ip")
    private String lastLoginIp;

    /** MAC address of last login (sent by client via X-Mac-Address header) */
    @Column(name = "last_login_mac")
    private String lastLoginMac;

    /** When was the password last changed */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /**
     * True when admin creates user — forces user to set own password via email link.
     * Set to false after first password change.
     */
    @Column(name = "force_password_change", nullable = false)
    private Boolean forcePasswordChange = false;

    /** Legacy reset token column — kept for backward compat; prefer email_verification_token table */
    @Column(name = "reset_password_token")
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry")
    private LocalDateTime resetPasswordTokenExpiry;

    /** Misc flag for special-case user handling */
    @Column(name = "check_if_specifice_user")
    private String checkIfSpecificeUser;

    @Column(name = "signature", length = 500)
    private String signature;

    // ── Transient (not persisted) ─────────────────────────────────────────────

    /** Populated at runtime for fine-grained permission checks if needed */
    @Transient
    private Set<String> permissions = new HashSet<>();

    // ── UserDetails implementation ────────────────────────────────────────────

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (role != null) {
            // Spring Security expects "ROLE_" prefix for hasRole() checks
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName().toUpperCase()));
        }
        return authorities;
    }

    /** Account expired = DELETED status */
    @Override
    public boolean isAccountNonExpired() {
        return status != Status.DELETED;
    }

    /**
     * Locked = explicitly locked OR temp-locked (lockedUntil in the future).
     * Auto-unlock: if lockedUntil is in the past we treat it as unlocked.
     * The AuthService also clears the lock in DB on next successful login.
     */
    @Override
    public boolean isAccountNonLocked() {
        if (Boolean.TRUE.equals(accountLocked)) {
            // Auto-expire temporary lockout
            if (lockedUntil != null && LocalDateTime.now().isAfter(lockedUntil)) {
                return true; // lockout window passed — auth service will clear DB flag
            }
            return false;
        }
        return status != Status.LOCKED;
    }

    /** Credentials never expire by policy — password age is enforced via force_password_change */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Active users only */
    @Override
    public boolean isEnabled() {
        return status == Status.ACTIVE;
    }
}
