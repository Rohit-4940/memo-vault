package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of a hash-dedupe check (Enterprise Phase 4): the content digest and any live documents the
 * caller may read that already hold byte-identical content.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckResult {
    private String sha256;
    private Long sizeBytes;
    private List<DocumentDto> matches;
}
