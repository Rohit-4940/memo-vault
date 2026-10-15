package com.nexmemo.web.service;

import com.nexmemo.core.dto.DocumentQuotaDto;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.core.repository.DocumentVersionRepository;
import com.nexmemo.web.config.DocumentProperties;
import com.nexmemo.web.exception.DocumentPolicyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-user vault storage quota (Enterprise Phase 4).
 *
 * <p>Usage is measured across <b>all revisions</b> of the user's live documents, because a superseded
 * revision still occupies disk. Enforcement is server-side and fail-closed: when the quota is
 * enabled an upload that would exceed it is rejected before any bytes are written.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentQuotaService {

    private final DocumentRecordRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentProperties properties;

    @Transactional(readOnly = true)
    public DocumentQuotaDto usage(Long userId) {
        long used = versionRepository.sumLiveVersionBytesByOwner(userId);
        long count = documentRepository.countByOwnerIdAndIsDeletedFalse(userId);
        if (!properties.getQuota().isEnabled()) {
            return DocumentQuotaDto.builder()
                    .usedBytes(used).limitBytes(null).remainingBytes(null)
                    .documentCount(count).unlimited(true).percentUsed(null)
                    .build();
        }
        long limit = properties.getQuota().getBytesPerUser();
        long remaining = Math.max(limit - used, 0);
        int percent = limit > 0 ? (int) Math.min(100, Math.round(used * 100.0 / limit)) : 0;
        return DocumentQuotaDto.builder()
                .usedBytes(used).limitBytes(limit).remainingBytes(remaining)
                .documentCount(count).unlimited(false).percentUsed(percent)
                .build();
    }

    /**
     * Rejects the write when it would push the owner over their configured quota. No-op while the
     * quota is disabled (usage is still tracked so it can be switched on without backfill).
     */
    @Transactional(readOnly = true)
    public void assertWithinQuota(Long userId, long incomingBytes) {
        if (!properties.getQuota().isEnabled()) {
            return;
        }
        long limit = properties.getQuota().getBytesPerUser();
        long used = versionRepository.sumLiveVersionBytesByOwner(userId);
        if (incomingBytes <= 0) {
            return;
        }
        if (used + incomingBytes > limit) {
            long remaining = Math.max(limit - used, 0);
            log.warn("Quota exceeded for user#{}: used={} incoming={} limit={}", userId, used, incomingBytes, limit);
            throw new DocumentPolicyException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Storage quota exceeded: " + remaining + " bytes remaining of " + limit);
        }
    }
}
