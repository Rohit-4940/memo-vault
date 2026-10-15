package com.nexmemo.core.enums;

/**
 * Confidentiality classification of a document. Purely descriptive in Phase 2; policy
 * enforcement (e.g. blocking external sharing of CONFIDENTIAL documents) is layered on in
 * later phases via the administration policy keys.
 */
public enum DocumentClassification {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    RESTRICTED
}
