package com.nexmemo.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A vault folder as returned to its owner. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentFolderDto {
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private Long ownerId;
    /** Number of live documents directly inside this folder. */
    private Long documentCount;
    private LocalDateTime createdDate;
}
