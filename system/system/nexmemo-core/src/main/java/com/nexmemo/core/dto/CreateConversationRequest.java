package com.nexmemo.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request to create (or get-or-create) a conversation.
 *
 * Only the fields relevant to {@code type} are used:
 * DIRECT → otherUserId; GROUP → title + memberIds; BRANCH → branchId;
 * DEPARTMENT → departmentId; MEMO → memoId.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {

    @NotBlank(message = "conversationType is required")
    private String conversationType;

    /** DIRECT: the other participant. */
    private Long otherUserId;

    private Long branchId;

    private Long departmentId;

    private Long memoId;

    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    /** GROUP: initial members (excluding creator). */
    private List<Long> memberIds;
}
