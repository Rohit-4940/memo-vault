package com.nexmemo.core.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for a chat attachment. Never carries the binary payload.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatAttachmentDto {
    private Long id;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private String scanStatus;
    private Long uploadedById;
    private LocalDateTime createdDate;
}