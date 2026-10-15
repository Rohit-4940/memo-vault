package com.nexmemo.core.dto;

import com.nexmemo.core.enums.PermissionSubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An explicit access grant on a document. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPermissionDto {
    private Long id;
    private Long documentId;
    private PermissionSubjectType subjectType;
    private Long subjectId;
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canDelete;
    private Boolean canShare;
    private Long grantedById;
}
