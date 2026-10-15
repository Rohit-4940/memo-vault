package com.nexmemo.web.service;

import com.nexmemo.core.entity.NotificationConfig;
import com.nexmemo.core.enums.NotificationChannel;
import com.nexmemo.core.event.MemoNotificationEvent;
import com.nexmemo.core.repository.NotificationConfigRepository;
import com.nexmemo.security.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Delivers memo lifecycle notifications over email (Phase 4a).
 *
 * <p>Consumes {@link MemoNotificationEvent}s <strong>after the publishing transaction commits</strong>
 * and on a separate thread ({@code @Async}). This is the key property required by the spec: a failed
 * or slow email can never block, throw into, or roll back a committed approval — by the time delivery
 * runs the business transaction is already durable.</p>
 *
 * <p>Every attempt is recorded in {@code notification_log} (SENT / FAILED / SKIPPED). Recipient emails
 * are never logged alongside secrets; only the address and event are recorded.</p>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private final NotificationConfigRepository notificationConfigRepository;
    private final NotificationLogService notificationLogService;
    private final NotificationTemplateRenderer renderer;
    private final EmailService emailService;

    @Value("${nexmemo.notifications.memo-link-base:}")
    private String memoLinkBase;

    /**
     * Transaction-aware entry point. Runs after commit; exceptions are swallowed so delivery problems
     * never propagate to the (already committed) caller.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMemoNotification(MemoNotificationEvent event) {
        try {
            dispatch(event);
        } catch (Exception e) {
            log.error("Notification dispatch failed for event={} memoId={}: {}",
                    event.getEventType(), event.getMemoId(), e.getMessage(), e);
        }
    }

    /** Package-private for direct unit testing of the delivery logic. */
    void dispatch(MemoNotificationEvent event) {
        if (isBlank(event.getRecipientEmail())) {
            log.warn("Skipping notification: recipient {} has no email address (event={} memoId={})",
                    event.getRecipientId(), event.getEventType(), event.getMemoId());
            record(event, "SKIPPED", "Recipient has no email address");
            return;
        }

        NotificationConfig config = notificationConfigRepository
                .findByEventTypeAndChannel(event.getEventType(), NotificationChannel.EMAIL)
                .orElse(null);

        if (config != null && Boolean.FALSE.equals(config.getIsActive())) {
            log.info("Notification channel disabled by config for event={} memoId={}",
                    event.getEventType(), event.getMemoId());
            record(event, "SKIPPED", "Channel disabled by notification config");
            return;
        }

        Map<String, Object> variables = variables(event);
        String subject = renderer.resolveSubject(config, event.getEventType(), variables);
        String body = renderer.resolveBody(config, event.getEventType(), variables);

        try {
            emailService.sendHtml(event.getRecipientEmail(), subject, body);
            record(event, "SENT", null);
        } catch (Exception e) {
            log.error("Email delivery failed to={} event={} memoId={}: {}",
                    event.getRecipientEmail(), event.getEventType(), event.getMemoId(), e.getMessage());
            record(event, "FAILED", e.getMessage());
        }
    }

    private Map<String, Object> variables(MemoNotificationEvent event) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", event.getRecipientName());
        vars.put("recipientEmail", event.getRecipientEmail());
        vars.put("memoId", event.getMemoId());
        vars.put("memoNo", event.getMemoNo());
        vars.put("memoTitle", event.getMemoTitle());
        vars.put("actorName", event.getActorName());
        vars.put("remarks", event.getRemarks());
        vars.put("event", event.getEventType() == null ? "" : event.getEventType().name());
        vars.put("link", buildLink(event.getMemoId()));
        return vars;
    }

    private String buildLink(Long memoId) {
        if (isBlank(memoLinkBase) || memoId == null) {
            return "";
        }
        String base = memoLinkBase.endsWith("/")
                ? memoLinkBase.substring(0, memoLinkBase.length() - 1)
                : memoLinkBase;
        return base + "/layout/memo/" + memoId;
    }

    private void record(MemoNotificationEvent event, String status, String error) {
        try {
            notificationLogService.logNotification(event.getMemoId(), event.getRecipientId(),
                    event.getEventType(), NotificationChannel.EMAIL, status, error);
        } catch (Exception e) {
            log.warn("Failed to persist notification log for memoId={}: {}", event.getMemoId(), e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
