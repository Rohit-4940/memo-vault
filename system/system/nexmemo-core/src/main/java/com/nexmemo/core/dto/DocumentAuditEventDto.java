package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A document audit entry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentAuditEventDto {
    private Long id;
    private Long documentId;
    private String action;
    private Long actorId;
    private String details;
    private String status;
    private LocalDateTime createdDate;
}
