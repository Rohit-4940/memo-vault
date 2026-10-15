package com.nexmemo.core.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5b — one entry of the self-service request catalogue.
 *
 * <p>A "request type" is an active {@link com.nexmemo.core.entity.MemoType} the caller is
 * permitted to raise. The dynamic form definition travels with the entry so a self-service client
 * can render the raise-request form without a second round-trip.</p>
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestTypeDto {

    private Long id;

    private String name;

    private String description;

    /** Parsed form schema of the memo type (empty list when none is configured). */
    @Builder.Default
    private List<FormField> fields = new ArrayList<>();
}
