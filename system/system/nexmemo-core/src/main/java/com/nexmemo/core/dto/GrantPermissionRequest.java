package com.nexmemo.core.dto;

import com.nexmemo.core.enums.PermissionSubjectType;
import lombok.Data;

/** Request body for granting a permission on a document. */
@Data
public class GrantPermissionRequest {
    private PermissionSubjectType subjectType;
    private Long subjectId;
    private Boolean canRead = true;
    private Boolean canWrite = false;
    private Boolean canDelete = false;
    private Boolean canShare = false;
}
