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
        AuditLog.AuditStatus auditStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                auditStatus = AuditLog.AuditStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid audit status filter: {}", status);
            }
        }
        String actionLike = (action != null && !action.isEmpty()) ? action : null;
        String usernameLike = (username != null && !username.isEmpty()) ? username : null;
        return auditLogRepository.findFiltered(actionLike, auditStatus, usernameLike, dateFrom, dateTo, pageable);
    }
}
