package com.nexmemo.web.service;

import com.nexmemo.core.dto.DocumentQuotaDto;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.core.repository.DocumentVersionRepository;
import com.nexmemo.web.config.DocumentProperties;
import com.nexmemo.web.exception.DocumentPolicyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for per-user storage quota accounting and fail-closed enforcement (Enterprise Phase 4).
 */
@ExtendWith(MockitoExtension.class)
class DocumentQuotaServiceTest {

    @Mock private DocumentRecordRepository documentRepository;
    @Mock private DocumentVersionRepository versionRepository;
    @Spy private DocumentProperties properties = new DocumentProperties();

    @InjectMocks private DocumentQuotaService service;

    private static final Long USER = 10L;

    @Test
    void usage_reportsUnlimitedWhenQuotaDisabled() {
        when(versionRepository.sumLiveVersionBytesByOwner(USER)).thenReturn(2048L);
        when(documentRepository.countByOwnerIdAndIsDeletedFalse(USER)).thenReturn(2L);

        DocumentQuotaDto usage = service.usage(USER);

        assertThat(usage.isUnlimited()).isTrue();
        assertThat(usage.getUsedBytes()).isEqualTo(2048L);
        assertThat(usage.getLimitBytes()).isNull();
        assertThat(usage.getRemainingBytes()).isNull();
        assertThat(usage.getDocumentCount()).isEqualTo(2L);
    }

    @Test
    void usage_computesRemainingAndPercentWhenEnabled() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setBytesPerUser(1000L);
        when(versionRepository.sumLiveVersionBytesByOwner(USER)).thenReturn(250L);
        when(documentRepository.countByOwnerIdAndIsDeletedFalse(USER)).thenReturn(1L);

        DocumentQuotaDto usage = service.usage(USER);

        assertThat(usage.isUnlimited()).isFalse();
        assertThat(usage.getLimitBytes()).isEqualTo(1000L);
        assertThat(usage.getRemainingBytes()).isEqualTo(750L);
        assertThat(usage.getPercentUsed()).isEqualTo(25);
    }

    @Test
    void assertWithinQuota_isNoOpWhenDisabled() {
        assertThatCode(() -> service.assertWithinQuota(USER, Long.MAX_VALUE)).doesNotThrowAnyException();
    }

    @Test
    void assertWithinQuota_allowsWhenUnderLimit() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setBytesPerUser(1000L);
        when(versionRepository.sumLiveVersionBytesByOwner(USER)).thenReturn(100L);

        assertThatCode(() -> service.assertWithinQuota(USER, 800L)).doesNotThrowAnyException();
    }

    @Test
    void assertWithinQuota_throws413WhenExceeded() {
        properties.getQuota().setEnabled(true);
        properties.getQuota().setBytesPerUser(1000L);
        when(versionRepository.sumLiveVersionBytesByOwner(USER)).thenReturn(900L);

        assertThatThrownBy(() -> service.assertWithinQuota(USER, 200L))
                .isInstanceOf(DocumentPolicyException.class)
                .satisfies(ex -> assertThat(((DocumentPolicyException) ex).getStatus())
                        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }
}
