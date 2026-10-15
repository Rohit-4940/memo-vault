package com.nexmemo.core.enums;

/**
 * The kind of principal a {@code document_permission} grant applies to.
 */
public enum PermissionSubjectType {
    /** A single user ({@code subject_id} = user id). */
    USER,
    /** Every user holding a role ({@code subject_id} = role id). */
    ROLE,
    /** A branch ({@code subject_id} = branch id) — members inherit the grant. */
    BRANCH,
    /** A department ({@code subject_id} = department id) — members inherit the grant. */
    DEPARTMENT,
    /**
     * A chat conversation ({@code subject_id} = conversation id, Phase 5) — the grant is evaluated
     * dynamically against the caller's live conversation access, so a member who leaves (or a
     * locked/archived conversation) loses the inherited read without any row being rewritten.
     */
    CONVERSATION
}
