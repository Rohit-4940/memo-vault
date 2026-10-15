package com.nexmemo.core.dto;

import com.nexmemo.core.entity.ApprovalHistory;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.MemoStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 5b — read-only tracking view of a self-service request (backed by a {@code Memo}).
 *
 * <p>Gives the requester a single payload with the current status, the approval history and the
 * workflow stage instances, so "where is my request?" needs one call.</p>
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestTrackingDto {

    private Long memoId;

    private String memoNo;

    private String title;

    private MemoStatus status;

    private Integer approvalLevel;

    private Integer totalApprovalLevels;

    private Long currentApproverId;

    private LocalDateTime submittedDate;

    private LocalDateTime approvedDate;

    @Builder.Default
    private List<ApprovalHistory> history = new ArrayList<>();

    @Builder.Default
    private List<WorkflowStageInstance> stages = new ArrayList<>();
}
