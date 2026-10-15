package com.nexmemo.core.enums;

/**
 * Lifecycle of a compression job.
 *
 * <p>Enterprise Phase 3 made compression asynchronous: a submitted job starts life {@link #QUEUED}
 * and is picked up by a bounded worker pool. {@link #PENDING} is retained only for backwards
 * compatibility with rows created before the asynchronous engine existed. {@link #CANCELLED} is
 * reachable while a job is queued (immediate) or running (cooperative).</p>
 */
public enum CompressionStatus {
    /** Legacy pre-async state; no longer produced. */
    PENDING,
    /** Accepted and waiting for a worker. */
    QUEUED,
    /** A worker is currently compressing (see {@code progressPercent} for detail). */
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    /** Cancelled by the owner before completion. */
    CANCELLED
}
