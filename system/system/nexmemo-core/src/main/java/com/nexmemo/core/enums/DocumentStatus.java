package com.nexmemo.core.enums;

/**
 * Lifecycle status of a canonical document record.
 *
 * <p>Phase 2 — document foundation. Deletion is modelled separately via
 * {@code document_record.is_deleted} (soft delete) so retention/audit history survives.</p>
 */
public enum DocumentStatus {
    /** Active and available. */
    ACTIVE,
    /** Locked for retention / no longer editable, still readable. */
    ARCHIVED,
    /** Under legal hold — cannot be deleted or purged. */
    LEGAL_HOLD
}
