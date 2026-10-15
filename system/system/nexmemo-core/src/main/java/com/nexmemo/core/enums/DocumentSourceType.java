package com.nexmemo.core.enums;

/**
 * Where a canonical document record originated. Lets the same underlying file be referenced
 * from a memo, a chat attachment or a document transfer without uncontrolled copies.
 */
public enum DocumentSourceType {
    /** Uploaded directly into the vault. */
    UPLOAD,
    /** Registered from a memo attachment. */
    MEMO,
    /** Registered from a chat attachment. */
    CHAT,
    /** Registered from a document transfer. */
    TRANSFER,
    /** Created by a processing job (compression, conversion, …). */
    PROCESSING
}
