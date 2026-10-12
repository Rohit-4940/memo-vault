package com.nexmemo.web.service;

import com.nexmemo.core.entity.AuditLog;
import com.nexmemo.security.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes chat-related audit entries (access denials, restricted reads, moderation actions).
 *
 * Uses {@link Propagation#REQUIRES_NEW} so the audit row is committed on its own connection:
 * it is never rolled back because the surrounding business transaction threw, and it does not
 * participate in the caller's (often read-only) transaction.
 *
 * Audit failures must never break the request path, so every write is best-effort.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAuditService {

    public static final String ACTION_ACCESS_DENIED = "CHAT_ACCESS_DENIED";
    public static final String ACTION_RESTRICTED_READ = "CHAT_RESTRICTED_READ";

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long performedById,
                    String action,
                    String entityType,
                    Long entityId,
                    String details,
                    AuditLog.AuditStatus status) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .performedById(performedById)
                    .details(details)
                    .status(status)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never let auditing break or mask the original request outcome.
            log.error("Failed to write chat audit entry action={} userId={}: {}", action, performedById, e.getMessage());
        }
    }

    public void accessDenied(Long performedById, String entityType, Long entityId, String reason) {
        log(performedById, ACTION_ACCESS_DENIED, entityType, entityId, reason, AuditLog.AuditStatus.SUSPICIOUS);
    }
}
