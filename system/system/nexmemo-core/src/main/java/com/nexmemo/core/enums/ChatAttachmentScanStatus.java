package com.nexmemo.core.enums;

/**
 * Malware scan outcome for a chat attachment.
 *
 * Downloads are always refused while the scan is anything other than {@code CLEAN}, so a file
 * that could not be scanned (service down, timeout) is never released — fail closed.
 *
 * @author : NexMemo
 **/
public enum ChatAttachmentScanStatus {
    PENDING,
    CLEAN,
    INFECTED,
    FAILED
}