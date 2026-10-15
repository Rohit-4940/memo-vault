package com.nexmemo.core.event;

import com.nexmemo.core.enums.NotificationEventType;
import lombok.Builder;
import lombok.Getter;

/**
 * Application-level payload describing a memo lifecycle event that should be delivered to a single
 * recipient (Phase 4a). Published inside the business transaction and consumed after commit by
 * {@code NotificationDispatchService}, so email delivery can never fail or roll back the memo
 * operation itself.
 *
 * <p>Carries everything the dispatcher needs without touching lazy associations: the recipient's
 * email/name are resolved by the publisher while the entities are still in scope.</p>
 *
 * @author : NexMemo
 **/
@Getter
@Builder
public class MemoNotificationEvent {

    private final NotificationEventType eventType;

    private final Long memoId;
    private final String memoNo;
    private final String memoTitle;

    private final Long recipientId;
    private final String recipientEmail;
    private final String recipientName;

    private final Long actorId;
    private final String actorName;

    private final String remarks;
}
