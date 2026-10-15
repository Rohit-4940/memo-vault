package com.nexmemo.web.service;

import com.nexmemo.core.dto.FormField;
import com.nexmemo.core.dto.RequestTrackingDto;
import com.nexmemo.core.dto.RequestTypeDto;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoType;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.MemoTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 5b — the self-service "common request engine".
 *
 * <p>Rather than introduce a parallel request domain, the engine sits on top of the memo engine:
 * a request <b>is</b> a {@link Memo}, a request <i>type</i> <b>is</b> an active
 * {@link MemoType} with a dynamic form schema, and approval runs through the existing
 * {@link WorkflowEngineService}. This keeps one approval truth, one numbering sequence, one audit
 * trail and one authorization model.</p>
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li><b>Catalogue</b> — the active request types the caller is permitted to raise.</li>
 *   <li><b>Raise / edit / submit / cancel</b> — a thin orchestration over {@link MemoService} that
 *       enforces resource-level authorization on every call.</li>
 *   <li><b>Track</b> — a single status + history + stage payload for "where is my request?".</li>
 * </ul>
 *
 * <p>Security: every read/write is authorized through {@link MemoAuthorizationService}; the caller
 * is always derived from the JWT principal, never from the payload. Admins keep configuration
 * authority but gain <b>no</b> approval bypass.</p>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RequestEngineService {

    private final MemoTypeRepository memoTypeRepository;
    private final MemoRepository memoRepository;
    private final MemoService memoService;
    private final MemoFormConfigService memoFormConfigService;
    private final MemoAuthorizationService memoAuthorizationService;
    private final WorkflowEngineService workflowEngineService;

    /** Active request types the caller may raise, each carrying its parsed dynamic form schema. */
    @Transactional(readOnly = true)
    public List<RequestTypeDto> getCatalogue(Long userId) {
        return memoTypeRepository.findAll().stream()
                .filter(type -> type.getStatus() == Status.ACTIVE)
                .filter(type -> memoAuthorizationService.canCreateMemoOfType(userId, type.getId()))
                .map(this::toTypeDto)
                .toList();
    }

    /**
     * Raises a request for the caller. When {@code submitNow} is true the freshly created draft is
     * submitted (routed into the workflow engine) in the same transaction.
     */
    public Memo createRequest(MemoService.CreateMemoRequest command, boolean submitNow, Long userId) {
        memoAuthorizationService.assertCanCreateMemoOfType(userId, command.getMemoTypeId());
        Memo memo = memoService.createMemo(command, userId);
        log.info("Request {} raised by userId={} (submitNow={})", memo.getMemoNo(), userId, submitNow);
        if (submitNow) {
            return memoService.submitMemo(memo.getId(), userId);
        }
        return memo;
    }

    /** Edits a request that is still editable (DRAFT / REVISION_REQUESTED). */
    public Memo updateRequest(Long memoId, MemoService.UpdateMemoRequest command, Long userId) {
        memoAuthorizationService.assertCanEditMemo(userId, memoId);
        return memoService.updateDraftMemo(memoId, command, userId);
    }

    /** Submits the caller's own request for approval. */
    public Memo submitRequest(Long memoId, Long userId) {
        memoAuthorizationService.assertCanEditMemo(userId, memoId);
        return memoService.submitMemo(memoId, userId);
    }

    /** Cancels the caller's own draft request. */
    public void cancelRequest(Long memoId, Long userId) {
        memoAuthorizationService.assertCanEditMemo(userId, memoId);
        memoService.cancelMemo(memoId, userId);
    }

    /** Reads a single request the caller is entitled to see. */
    @Transactional(readOnly = true)
    public Memo getRequest(Long memoId, Long userId) {
        memoAuthorizationService.assertCanViewMemo(userId, memoId);
        return memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + memoId));
    }

    /** The caller's own requests ("my requests"), newest first via the supplied sort. */
    @Transactional(readOnly = true)
    public Page<Memo> getMyRequests(Long userId, Pageable pageable) {
        return memoRepository.findByCreatedBy(userId, pageable);
    }

    /** Status + approval history + workflow stages for one request. */
    @Transactional(readOnly = true)
    public RequestTrackingDto trackRequest(Long memoId, Long userId) {
        memoAuthorizationService.assertCanViewMemo(userId, memoId);
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Request not found with id: " + memoId));
        return RequestTrackingDto.builder()
                .memoId(memo.getId())
                .memoNo(memo.getMemoNo())
                .title(memo.getTitle())
                .status(memo.getStatus())
                .approvalLevel(memo.getApprovalLevel())
                .totalApprovalLevels(memo.getTotalApprovalLevels())
                .currentApproverId(memo.getCurrentApprover() != null ? memo.getCurrentApprover().getId() : null)
                .submittedDate(memo.getSubmittedDate())
                .approvedDate(memo.getApprovedDate())
                .history(memoService.getApprovalHistory(memoId))
                .stages(workflowEngineService.getStageInstances(memoId))
                .build();
    }

    private RequestTypeDto toTypeDto(MemoType type) {
        List<FormField> fields = memoFormConfigService.parseSchema(type);
        return RequestTypeDto.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .fields(fields)
                .build();
    }
}
