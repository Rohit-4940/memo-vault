package com.nexmemo.web.service;

/**
 * Raised when an uploaded archive violates a guardrail (bomb, excessive entries, path traversal,
 * disallowed nesting). Treated as a normal, reportable job failure rather than a system error.
 */
public class ArchiveSecurityException extends RuntimeException {

    public ArchiveSecurityException(String message) {
        super(message);
    }
}
