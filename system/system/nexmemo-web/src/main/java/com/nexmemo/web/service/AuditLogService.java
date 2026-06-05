package com.nexmemo.web.service;

import com.nexmemo.core.entity.AuditLog;
import com.nexmemo.security.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLog> getFilteredAuditLogs(String action, String status, String username,
                                                LocalDateTime dateFrom, LocalDateTime dateTo,
                                                Pageable pageable) {
        AuditLog.AuditStatus auditStatus = parseStatus(status);
        String actionLike = (action != null && !action.isEmpty()) ? action : null;
        String usernameLike = (username != null && !username.isEmpty()) ? username : null;
        return auditLogRepository.findFiltered(actionLike, auditStatus, usernameLike, dateFrom, dateTo, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getFilteredAuditLogs(String action, String status, String username,
                                                LocalDateTime dateFrom, LocalDateTime dateTo,
                                                Long userId, Pageable pageable) {
        if (userId != null && (username == null || username.isEmpty())) {
            AuditLog.AuditStatus auditStatus = parseStatus(status);
            String actionLike = (action != null && !action.isEmpty()) ? action : null;
            return auditLogRepository.findFilteredByUser(actionLike, auditStatus, dateFrom, dateTo, userId, pageable);
        }
        return getFilteredAuditLogs(action, status, username, dateFrom, dateTo, pageable);
    }

    private AuditLog.AuditStatus parseStatus(String status) {
        if (status != null && !status.isEmpty()) {
            try {
                return AuditLog.AuditStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid audit status filter: {}", status);
            }
        }
        return null;
    }
}
