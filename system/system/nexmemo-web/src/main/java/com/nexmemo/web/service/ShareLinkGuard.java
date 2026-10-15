package com.nexmemo.web.service;

import com.nexmemo.core.entity.DocumentShareLink;
import com.nexmemo.core.repository.DocumentShareLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Single place where a public share link is validated (Enterprise Phase 4).
 *
 * <p>Extracted so the download path and the inline-preview path cannot drift apart: both must reject
 * revoked, expired, exhausted and password-mismatched links, and both must be checked on
 * <b>every</b> access. The opaque token is the only credential and the document id is never
 * revealed to the visitor.</p>
 *
 * @author : NexMemo
 */
@Component
@RequiredArgsConstructor
public class ShareLinkGuard {

    private final DocumentShareLinkRepository shareLinkRepository;
    private final PasswordEncoder passwordEncoder;

    /** Loads a link that still exists and is not revoked or expired (password not yet checked). */
    @Transactional(readOnly = true)
    public DocumentShareLink load(String token) {
        DocumentShareLink link = shareLinkRepository.findByToken(token)
                .orElseThrow(() -> new AccessDeniedException("Share link is invalid"));
        if (Boolean.TRUE.equals(link.getRevoked())) {
            throw new AccessDeniedException("Share link has been revoked");
        }
        if (link.getExpiresAt() != null && LocalDateTime.now().isAfter(link.getExpiresAt())) {
            throw new AccessDeniedException("Share link has expired");
        }
        return link;
    }

    /** Rejects a link whose download cap has been reached. */
    public void assertUsable(DocumentShareLink link) {
        if (link.getMaxDownloads() != null && link.getDownloadCount() != null
                && link.getDownloadCount() >= link.getMaxDownloads()) {
            throw new AccessDeniedException("Share link download limit reached");
        }
    }

    /** Rejects a missing/incorrect password for a protected link. */
    public void assertPassword(DocumentShareLink link, String password) {
        if (link.getPasswordHash() != null
                && (password == null || !passwordEncoder.matches(password, link.getPasswordHash()))) {
            throw new AccessDeniedException("Share link password is incorrect");
        }
    }

    /** Full validation for an actual redemption: existence, revocation, expiry, cap and password. */
    public DocumentShareLink validate(String token, String password) {
        DocumentShareLink link = load(token);
        assertUsable(link);
        assertPassword(link, password);
        return link;
    }

    public boolean passwordRequired(DocumentShareLink link) {
        return link.getPasswordHash() != null;
    }

    /** Remaining downloads, or null when unlimited. */
    public Integer remaining(DocumentShareLink link) {
        if (link.getMaxDownloads() == null) {
            return null;
        }
        int used = link.getDownloadCount() == null ? 0 : link.getDownloadCount();
        return Math.max(link.getMaxDownloads() - used, 0);
    }
}
