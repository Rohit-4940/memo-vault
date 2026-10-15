package com.nexmemo.core.dto;

import lombok.Data;

/** Create/update a vault folder. */
@Data
public class SaveFolderRequest {
    private String name;
    /** Parent folder id; null = root. */
    private Long parentId;
}
