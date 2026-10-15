package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per-user vault storage usage against the configured quota. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentQuotaDto {
    private Long usedBytes;
    private Long limitBytes;
    private Long remainingBytes;
    private Long documentCount;
    /** True when no quota is configured (limitBytes is null). */
    private boolean unlimited;
    /** Percentage of the quota consumed (0-100), or null when unlimited. */
    private Integer percentUsed;
}
