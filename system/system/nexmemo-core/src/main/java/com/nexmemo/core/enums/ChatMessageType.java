package com.nexmemo.core.enums;

/**
 * Type of a chat message payload.
 *
 * @author : NexMemo
 **/
public enum ChatMessageType {
    TEXT,
    SYSTEM,
    MEMO_REF,
    FILE,
    /** A message copied into another conversation (Phase 2e). */
    FORWARD
}
