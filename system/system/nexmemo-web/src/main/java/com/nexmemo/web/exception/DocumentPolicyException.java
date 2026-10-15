package com.nexmemo.web.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A server-enforced document policy violation (quota exceeded, preview not available, …). Carries
 * the HTTP status the client should see so a policy decision maps to a meaningful response instead
 * of a generic 500.
 */
@Getter
public class DocumentPolicyException extends RuntimeException {

    private final HttpStatus status;

    public DocumentPolicyException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
