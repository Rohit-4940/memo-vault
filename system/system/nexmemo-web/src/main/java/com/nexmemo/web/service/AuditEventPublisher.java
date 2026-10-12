package com.nexmemo.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmemo.core.entity.AuditLog;
import com.nexmemo.security.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central audit publisher for business-sensitive operations (memo transitions, administrative
 * and configuration changes).
 *
 * <p>Design rules:</p>
 * <ul>
 *   <li>Writes run in {@link Propagation#REQUIRES_NEW} so an audit row is committed on its own
 *       connection: it survives a business rollback and never joins the caller's transaction.</li>
 *   <li>Auditing is <b>best effort</b>: a failure to persist must never break or mask the
 *       original request outcome.</li>
 *   <li>Before/after snapshots are serialised to JSON and <b>secret values are redacted</b>
 *       before they reach the database — passwords, tokens, OTPs and the like never appear
 *       in audit records.</li>
 * </ul>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventPublisher {

    public static final String ACTION_ACCESS_DENIED = "ACCESS_DENIED";

    /**
     * Field names (normalised: lower-case, underscores removed) whose values are replaced with
     * {@code "***"} before persistence. Kept intentionally small and explicit.
     */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "accesstoken", "refreshtoken",
            "resetpasswordtoken", "otp", "pin", "cvv", "cardnumber", "accountnumber",
            "apikey", "clientsecret", "privatekey");

    private static final String REDACTED = "***";

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Writes a single audit row. Never propagates an exception to the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long performedById, String action, String entityType, Long entityId,
                    String details, AuditLog.AuditStatus status) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .performedById(performedById)
                    .details(details)
                    .status(status)
                    .build());
        } catch (Exception e) {
            // Never let auditing break or mask the original request outcome.
            log.error("Audit write failed action={} entity={}#{} by={}: {}",
                    action, entityType, entityId, performedById, e.getMessage());
        }
    }

    /**
     * Records an entity/configuration change together with redacted before/after snapshots.
     * Callers pass plain maps (e.g. {@code Map.of("status", old, "remarks", r)}).
     */
    public void recordChange(Long performedById, String action, String entityType, Long entityId,
                             Object before, Object after) {
        log(performedById, action, entityType, entityId,
                buildChangeDetails(before, after), AuditLog.AuditStatus.SUCCESS);
    }

    /** Records a denied access attempt as a suspicious audit event. */
    public void accessDenied(Long performedById, String entityType, Long entityId, String reason) {
        log(performedById, ACTION_ACCESS_DENIED, entityType, entityId, reason,
                AuditLog.AuditStatus.SUSPICIOUS);
    }

    private String buildChangeDetails(Object before, Object after) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", redact(before));
        payload.put("after", redact(after));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Audit payload serialization failed: {}", e.getMessage());
            return "{\"before\":\"" + safeString(before) + "\",\"after\":\"" + safeString(after) + "\"}";
        }
    }

    private Object redact(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return value;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            String key = String.valueOf(k);
            out.put(key, isSensitive(key) ? REDACTED : v);
        });
        return out;
    }

    private boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key.toLowerCase().replace("_", ""));
    }

    private String safeString(Object value) {
        return value == null ? "" : String.valueOf(value).replace("\"", "'");
    }
}
