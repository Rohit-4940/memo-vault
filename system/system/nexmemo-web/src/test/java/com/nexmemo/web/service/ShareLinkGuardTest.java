package com.nexmemo.web.service;

import com.nexmemo.core.entity.DocumentShareLink;
import com.nexmemo.core.repository.DocumentShareLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Security tests for {@link ShareLinkGuard} — the single place a public share link is validated:
 * existence, revocation, expiry, download cap and password. Both the download and the preview paths
 * depend on these rules, so they are asserted here in isolation.
 */
@ExtendWith(MockitoExtension.class)
class ShareLinkGuardTest {

    @Mock private DocumentShareLinkRepository shareLinkRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private ShareLinkGuard guard;

    private DocumentShareLink link() {
        DocumentShareLink l = new DocumentShareLink();
        l.setId(700L);
        l.setDocumentId(100L);
        l.setToken("tok");
        l.setRevoked(false);
        l.setDownloadCount(0);
        return l;
    }

    @Test
    void load_rejectsUnknownToken() {
        when(shareLinkRepository.findByToken("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.load("nope")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void load_rejectsRevoked() {
        DocumentShareLink l = link();
        l.setRevoked(true);
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> guard.load("tok")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void load_rejectsExpired() {
        DocumentShareLink l = link();
        l.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> guard.load("tok")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void load_ignoresPasswordSoLandingCanRender() {
        DocumentShareLink l = link();
        l.setPasswordHash("HASHED");
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        assertThat(guard.load("tok")).isSameAs(l);
        assertThat(guard.passwordRequired(l)).isTrue();
    }

    @Test
    void validate_rejectsWhenDownloadCapReached() {
        DocumentShareLink l = link();
        l.setMaxDownloads(5);
        l.setDownloadCount(5);
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> guard.validate("tok", null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validate_rejectsIncorrectPassword() {
        DocumentShareLink l = link();
        l.setPasswordHash("HASHED");
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);
        assertThatThrownBy(() -> guard.validate("tok", "wrong")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validate_rejectsMissingPasswordForProtectedLink() {
        DocumentShareLink l = link();
        l.setPasswordHash("HASHED");
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        assertThatThrownBy(() -> guard.validate("tok", null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void validate_acceptsCorrectPassword() {
        DocumentShareLink l = link();
        l.setPasswordHash("HASHED");
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        when(passwordEncoder.matches("right", "HASHED")).thenReturn(true);
        assertThat(guard.validate("tok", "right")).isSameAs(l);
    }

    @Test
    void validate_acceptsUnprotectedLinkUnderCap() {
        DocumentShareLink l = link();
        l.setMaxDownloads(5);
        when(shareLinkRepository.findByToken("tok")).thenReturn(Optional.of(l));
        assertThatCode(() -> guard.validate("tok", null)).doesNotThrowAnyException();
    }

    @Test
    void remaining_isNullWhenUnlimitedAndCountsDownWhenCapped() {
        DocumentShareLink unlimited = link();
        assertThat(guard.remaining(unlimited)).isNull();

        DocumentShareLink capped = link();
        capped.setMaxDownloads(5);
        capped.setDownloadCount(2);
        assertThat(guard.remaining(capped)).isEqualTo(3);

        capped.setDownloadCount(9);
        assertThat(guard.remaining(capped)).isZero();
    }
}
