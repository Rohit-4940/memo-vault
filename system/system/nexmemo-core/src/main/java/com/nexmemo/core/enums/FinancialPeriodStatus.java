package com.nexmemo.core.enums;

/**
 * Lifecycle of a financial period inside a fiscal year (Phase 4b).
 *
 * <ul>
 *   <li>{@code OPEN} — currently accepting memo creation / transactions.</li>
 *   <li>{@code CLOSED} — no longer open; may be reopened by an administrator.</li>
 *   <li>{@code LOCKED} — terminal, read-only; cannot be reopened.</li>
 * </ul>
 *
 * @author : NexMemo
 **/
public enum FinancialPeriodStatus {
    OPEN,
    CLOSED,
    LOCKED
}