package com.nexmemo.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmemo.core.entity.AuditLog;
import com.nexmemo.security.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the general audit publisher (Phase 3): before/after capture with secret
 * redaction, and best-effort behaviour that never breaks the caller.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private AuditEventPublisher publisher;

    @Test
    void recordChange_redactsSensitiveFields_andKeepsOthers() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", "DRAFT");
        before.put("password", "SuperSecret123");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", "PENDING_APPROVAL");
        after.put("access_token", "abc.def.ghi");
        after.put("remarks", "please approve");

        publisher.recordChange(7L, "MEMO_SUBMITTED", "MEMO", 42L, before, after);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getAction()).isEqualTo("MEMO_SUBMITTED");
        assertThat(saved.getEntityType()).isEqualTo("MEMO");
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getPerformedById()).isEqualTo(7L);
        assertThat(saved.getStatus()).isEqualTo(AuditLog.AuditStatus.SUCCESS);

        assertThat(saved.getDetails())
                .contains("\"status\"", "DRAFT", "PENDING_APPROVAL", "please approve")
                .contains("\"password\":\"***\"")
                .contains("\"access_token\":\"***\"")
                .doesNotContain("SuperSecret123")
                .doesNotContain("abc.def.ghi");
    }

    @Test
    void accessDenied_writesSuspiciousEvent() {
        publisher.accessDenied(9L, "MEMO", 5L, "memo view denied");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();

        assertThat(saved.getAction()).isEqualTo(AuditEventPublisher.ACTION_ACCESS_DENIED);
        assertThat(saved.getStatus()).isEqualTo(AuditLog.AuditStatus.SUSPICIOUS);
        assertThat(saved.getDetails()).isEqualTo("memo view denied");
    }

    @Test
    void log_swallowsRepositoryFailure_neverThrows() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        // Must not propagate — auditing is best effort.
        publisher.log(1L, "MEMO_APPROVED", "MEMO", 2L, "detail", AuditLog.AuditStatus.SUCCESS);

        verify(auditLogRepository).save(any(AuditLog.class));
    }
}
