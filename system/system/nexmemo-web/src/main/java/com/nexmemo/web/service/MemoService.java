package com.nexmemo.web.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.event.MemoNotificationEvent;
import com.nexmemo.core.enums.LeaveStatus;
import com.nexmemo.core.enums.MemoRoutingStatus;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.NotificationEventType;
import com.nexmemo.core.enums.Priority;
import com.nexmemo.core.repository.*;
import com.nexmemo.security.repository.BranchRepository;
import com.nexmemo.core.repository.MemoTemplateRepository;
import com.nexmemo.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MemoService {

    private final MemoRepository memoRepository;
    private final MemoRoutingRepository memoRoutingRepository;
    private final MemoDocumentRepository memoDocumentRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final MemoTypeRepository memoTypeRepository;
    private final BudgetAllocationRepository budgetAllocationRepository;
    private final LeaveTrackerRepository leaveTrackerRepository;
    private final MemoCcRepository memoCcRepository;
    private final MemoTemplateRepository memoTemplateRepository;
    private final ApprovalEngineService approvalEngineService;
    private final NotificationService notificationService;
    private final com.nexmemo.web.storage.StorageService storageService;
    private final WorkflowEngineService workflowEngineService;
    private final MemoNumberGeneratorService memoNumberGeneratorService;
    private final MemoCategoryRepository memoCategoryRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final MemoAuthorizationService memoAuthorizationService;
    private final ApplicationEventPublisher notificationEventPublisher;
    private final FinancialPeriodService financialPeriodService;
    private final MemoFormConfigService memoFormConfigService;
    private final MemoVersionService memoVersionService;

    @Data
    public static class CreateMemoRequest {
        @NotBlank
        private String title;
        private String description;
        private String content;
        private Long memoTypeId;
        private Long departmentId;
        private Long branchId;
        private Priority priority;
        private BigDecimal amount;
        private Long templateId;
        private List<Long> ccUserIds;
        private Long categoryId;
        /** Dynamic values captured by the memo type's form schema (Phase 4c). */
        private Map<String, Object> formValues;
        private String paymentMethod;
        private String budgetCode;
        private String vendorName;
        private String vendorAccount;
        private Boolean isUrgent;
        private LocalDate expectedDate;
    }

    /**
     * Phase 5b — partial update of a request that is still editable (DRAFT / REVISION_REQUESTED).
     * Every field is optional: only non-null values are applied, so the self-service edit form can
     * send a delta. Dynamic form values are re-validated against the (possibly new) type schema and
     * a {@code memo_version} snapshot is appended so edit history is real.
     */
    @Data
    public static class UpdateMemoRequest {
        private String title;
        private String description;
        private String content;
        private Long memoTypeId;
        private Long departmentId;
        private Long branchId;
        private Long categoryId;
        private Long templateId;
        private Priority priority;
        private BigDecimal amount;
        private String paymentMethod;
        private String budgetCode;
        private String vendorName;
        private String vendorAccount;
        private Boolean isUrgent;
        private LocalDate expectedDate;
        private Map<String, Object> formValues;
    }

    @Transactional(readOnly = true)
    public List<Memo> getAllMemos(Long userId) {
        if (memoAuthorizationService.isAdmin(userId)) {
            return memoRepository.findAll();
        }
        return memoRepository.findAll(
                MemoSpecifications.notDeleted().and(MemoSpecifications.accessibleBy(userId)));
    }

    @Transactional(readOnly = true)
    public Page<Memo> getAllMemosPaginated(Long userId, Pageable pageable) {
        if (memoAuthorizationService.isAdmin(userId)) {
            return memoRepository.findAllActive(pageable);
        }
        return memoRepository.findAll(
                MemoSpecifications.notDeleted().and(MemoSpecifications.accessibleBy(userId)), pageable);
    }

    @Transactional(readOnly = true)
    public Memo getMemoById(Long id) {
        return memoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Memo> getMemosByCreator(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        return memoRepository.findByCreatedBy(userId);
    }

    @Transactional(readOnly = true)
    public Page<Memo> getMemosByCreatorPaginated(Long userId, Pageable pageable) {
        return memoRepository.findByCreatedBy(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Memo> getMemosByStatus(Long userId, MemoStatus status) {
        if (memoAuthorizationService.isAdmin(userId)) {
            return memoRepository.findByStatus(status);
        }
        return memoRepository.findAll(MemoSpecifications.notDeleted()
                .and(MemoSpecifications.accessibleBy(userId))
                .and(MemoSpecifications.withStatus(status)));
    }

    @Transactional(readOnly = true)
    public List<Memo> getMemosByBranch(Long branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw new EntityNotFoundException("Branch not found with id: " + branchId);
        }
        return memoRepository.findByBranchId(branchId);
    }

    @Transactional(readOnly = true)
    public List<Memo> searchMemos(Long userId, String keyword) {
        if (memoAuthorizationService.isAdmin(userId)) {
            return memoRepository.findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(keyword);
        }
        return memoRepository.findAll(MemoSpecifications.notDeleted()
                .and(MemoSpecifications.accessibleBy(userId))
                .and(MemoSpecifications.titleOrMemoNoContains(keyword)));
    }

    @Transactional(readOnly = true)
    public List<Memo> getPendingApprovals(Long approverUserId) {
        if (!userRepository.existsById(approverUserId)) {
            throw new EntityNotFoundException("User not found with id: " + approverUserId);
        }
        return memoRepository.findByCurrentApproverIdAndStatusNotIn(approverUserId,
                List.of(MemoStatus.APPROVED, MemoStatus.REJECTED, MemoStatus.CANCELLED));
    }

    public Memo createMemo(CreateMemoRequest request, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + creatorId));

        MemoType memoType = null;
        if (request.getMemoTypeId() != null) {
            memoType = memoTypeRepository.findById(request.getMemoTypeId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoType not found with id: " + request.getMemoTypeId()));
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + request.getDepartmentId()));
        }

        Branch branch = null;
        if (request.getBranchId() != null) {
            branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new EntityNotFoundException("Branch not found with id: " + request.getBranchId()));
        }

        MemoTemplate template = null;
        if (request.getTemplateId() != null) {
            template = memoTemplateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoTemplate not found with id: " + request.getTemplateId()));
            log.debug("Template {} loaded for memo creation", request.getTemplateId());
        }

        MemoCategory category = null;
        if (request.getCategoryId() != null) {
            category = memoCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoCategory not found: " + request.getCategoryId()));
        }

        // Phase 4c — validate dynamic form values against the type's schema and the template's
        // required-fields list, then enforce the template's amount ceiling.
        memoFormConfigService.validateFormValues(memoType, template, request.getFormValues());
        memoFormConfigService.validateAmount(request.getAmount(), template);

        Memo memo = new Memo();
        memo.setTitle(request.getTitle());
        memo.setDescription(request.getDescription());
        memo.setContent(request.getContent() != null ? request.getContent()
                : (template != null ? template.getContent() : null));
        memo.setFormValues(memoFormConfigService.toJson(request.getFormValues()));
        assignMemoNumberAndFiscalYear(memo);
        memo.setStatus(MemoStatus.DRAFT);
        memo.setPriority(resolvePriority(request, template));
        memo.setMemoType(memoType);
        memo.setDepartment(department);
        memo.setBranch(branch);
        memo.setCategory(category);
        memo.setAmount(request.getAmount() != null ? request.getAmount()
                : (template != null ? template.getDefaultAmount() : null));
        memo.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod()
                : (template != null ? template.getDefaultPaymentMethod() : null));
        memo.setBudgetCode(request.getBudgetCode());
        memo.setVendorName(request.getVendorName());
        memo.setVendorAccount(request.getVendorAccount());
        memo.setIsUrgent(request.getIsUrgent() != null ? request.getIsUrgent() : false);
        memo.setExpectedDate(request.getExpectedDate());
        if (template != null && template.getRequiredApprovalLevels() != null) {
            memo.setTotalApprovalLevels(template.getRequiredApprovalLevels());
        }

        memo = memoRepository.save(memo);
        log.info("Memo created: id={} memoNo={} by userId={}", memo.getId(), memo.getMemoNo(), creatorId);

        // Phase 4c — persist the v1 content/form snapshot so memo-version history is real.
        memoVersionService.snapshot(memo, creatorId);

        final Memo savedMemo = memo;

        if (request.getCcUserIds() != null) {
            for (Long ccUserId : request.getCcUserIds()) {
                userRepository.findById(ccUserId).ifPresent(ccUser -> {
                    MemoCc cc = new MemoCc();
                    cc.setMemo(savedMemo);
                    cc.setUser(ccUser);
                    cc.setAcknowledged(false);
                    memoCcRepository.save(cc);
                    log.debug("CC user {} added to memo {}", ccUserId, savedMemo.getId());
                });
            }
        }

        log.info("Memo created complete: {} by user {}", memo.getMemoNo(), creatorId);
        return memo;
    }

    /**
     * Phase 5b — edits a request while it is still editable (DRAFT / REVISION_REQUESTED). Only the
     * creator (or an admin) may edit, the dynamic values are re-validated against the effective memo
     * type's schema, and a version snapshot is appended so the edit is auditable.
     */
    public Memo updateDraftMemo(Long memoId, UpdateMemoRequest request, Long userId) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.DRAFT && memo.getStatus() != MemoStatus.REVISION_REQUESTED) {
            throw new IllegalStateException(
                    "Only DRAFT or REVISION_REQUESTED requests can be edited. Current status: " + memo.getStatus());
        }
        if (!memoAuthorizationService.isAdmin(userId) && !memo.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Only the creator can edit the request");
        }

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), null);

        MemoType memoType = memo.getMemoType();
        if (request.getMemoTypeId() != null && (memoType == null || !request.getMemoTypeId().equals(memoType.getId()))) {
            memoType = memoTypeRepository.findById(request.getMemoTypeId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoType not found with id: " + request.getMemoTypeId()));
        }

        MemoTemplate template = null;
        if (request.getTemplateId() != null) {
            template = memoTemplateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoTemplate not found with id: " + request.getTemplateId()));
        }

        // Validate the effective dynamic values (new values when supplied, else the stored ones) and
        // the effective amount ceiling before mutating anything.
        Map<String, Object> effectiveFormValues = request.getFormValues() != null
                ? request.getFormValues()
                : memoFormConfigService.parseFormValues(memo);
        memoFormConfigService.validateFormValues(memoType, template, effectiveFormValues);
        BigDecimal effectiveAmount = request.getAmount() != null ? request.getAmount() : memo.getAmount();
        memoFormConfigService.validateAmount(effectiveAmount, template);

        if (request.getTitle() != null) {
            if (request.getTitle().isBlank()) {
                throw new IllegalArgumentException("Request title must not be blank");
            }
            memo.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) memo.setDescription(request.getDescription());
        if (request.getContent() != null) memo.setContent(request.getContent());
        memo.setMemoType(memoType);
        if (request.getDepartmentId() != null) {
            memo.setDepartment(departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + request.getDepartmentId())));
        }
        if (request.getBranchId() != null) {
            memo.setBranch(branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new EntityNotFoundException("Branch not found with id: " + request.getBranchId())));
        }
        if (request.getCategoryId() != null) {
            memo.setCategory(memoCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoCategory not found: " + request.getCategoryId())));
        }
        if (request.getPriority() != null) memo.setPriority(request.getPriority());
        if (request.getAmount() != null) memo.setAmount(request.getAmount());
        if (request.getPaymentMethod() != null) memo.setPaymentMethod(request.getPaymentMethod());
        if (request.getBudgetCode() != null) memo.setBudgetCode(request.getBudgetCode());
        if (request.getVendorName() != null) memo.setVendorName(request.getVendorName());
        if (request.getVendorAccount() != null) memo.setVendorAccount(request.getVendorAccount());
        if (request.getIsUrgent() != null) memo.setIsUrgent(request.getIsUrgent());
        if (request.getExpectedDate() != null) memo.setExpectedDate(request.getExpectedDate());
        if (request.getFormValues() != null) memo.setFormValues(memoFormConfigService.toJson(request.getFormValues()));

        memo = memoRepository.save(memo);
        memoVersionService.snapshot(memo, userId);
        log.info("Request {} edited by userId={}", memo.getMemoNo(), userId);

        auditEventPublisher.recordChange(userId, ACTION_UPDATED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), null));
        return memo;
    }

    public Memo submitMemo(Long memoId, Long userId) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.DRAFT && memo.getStatus() != MemoStatus.REVISION_REQUESTED) {
            throw new IllegalStateException("Only DRAFT or REVISION_REQUESTED memos can be submitted. Current status: " + memo.getStatus());
        }

        if (!memo.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Only the creator can submit the memo");
        }

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), null);

        if (workflowEngineService.hasActiveConfigForMemoType(memoTypeIdOf(memo)) || memo.getCategory() != null) {
            Memo submitted = workflowEngineService.submitMemo(memo, userId);
            auditEventPublisher.recordChange(userId, ACTION_SUBMITTED, ENTITY_MEMO, memoId, before,
                    stateSnapshot(submitted.getStatus(), submitted.getApprovalLevel(), approverIdOf(submitted), null));
            return submitted;
        }

        int totalLevels = approvalEngineService.determineApprovalLevels(memo);
        memo.setTotalApprovalLevels(totalLevels);
        memo.setApprovalLevel(0);
        memo.setStatus(MemoStatus.PENDING_APPROVAL);
        memo.setSubmittedDate(LocalDateTime.now());

        User nextApprover = approvalEngineService.getNextApprover(memo, 0);
        memo.setCurrentApprover(nextApprover);

        memo = memoRepository.save(memo);

        ApprovalHistory history = new ApprovalHistory();
        history.setMemo(memo);
        history.setApprover(userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId)));
        history.setAction(MemoStatus.PENDING_APPROVAL);
        history.setRemarks("Submitted for approval");
        history.setApprovalLevel(0);
        history.setActionDate(LocalDateTime.now());
        approvalHistoryRepository.save(history);

        log.info("Memo {} submitted by user {}, next approver: {}", memo.getMemoNo(), userId, nextApprover.getId());

        notificationService.createNotification(
                nextApprover.getId(),
                "Memo Pending Approval",
                "Memo " + memo.getMemoNo() + " (" + memo.getTitle() + ") requires your approval",
                "MEMO_APPROVAL", memo.getId(), "MEMO");
        log.debug("Notification sent to approver userId={} for memo {}", nextApprover.getId(), memo.getId());

        emailNotify(NotificationEventType.MEMO_SUBMITTED, memo, nextApprover,
                userId, nameOrNull(userId), null);

        auditEventPublisher.recordChange(userId, ACTION_SUBMITTED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), null));

        return memo;
    }

    public Memo approveMemo(Long memoId, Long approverId, String remarks) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Memo is not pending approval. Current status: " + memo.getStatus());
        }

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks);

        if (workflowEngineService.hasActiveConfigForMemoType(memoTypeIdOf(memo)) || memo.getCategory() != null) {
            WorkflowStageInstance current = workflowEngineService.getCurrentStage(memoId);
            if (current != null) {
                workflowEngineService.approveStage(memoId, current.getId(), approverId, remarks);
            }
            Memo after = memoRepository.findById(memoId).orElse(memo);
            auditEventPublisher.recordChange(approverId, ACTION_APPROVED, ENTITY_MEMO, memoId, before,
                    stateSnapshot(after.getStatus(), after.getApprovalLevel(), approverIdOf(after), remarks));
            return after;
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + approverId));

        if (!approvalEngineService.hasAuthorityToApprove(approver, memo)) {
            throw new IllegalStateException("User " + approverId + " does not have authority to approve this memo");
        }

        int currentLevel = memo.getApprovalLevel();

        ApprovalHistory history = new ApprovalHistory();
        history.setMemo(memo);
        history.setApprover(approver);
        history.setAction(MemoStatus.valueOf("LEVEL_" + currentLevel + "_APPROVED"));
        history.setRemarks(remarks);
        history.setApprovalLevel(currentLevel + 1);
        history.setActionDate(LocalDateTime.now());
        approvalHistoryRepository.save(history);

        int newLevel = currentLevel + 1;
        memo.setApprovalLevel(newLevel);

        if (newLevel >= memo.getTotalApprovalLevels()) {
            memo.setStatus(MemoStatus.APPROVED);
            memo.setApprovedDate(LocalDateTime.now());
            memo.setCurrentApprover(null);
            log.info("Memo {} fully approved", memo.getMemoNo());

            notificationService.createNotification(
                    memo.getCreatedBy(),
                    "Memo Approved",
                    "Your memo " + memo.getMemoNo() + " has been fully approved",
                    "MEMO_APPROVED", memo.getId(), "MEMO");

            emailNotify(NotificationEventType.MEMO_APPROVED, memo, userOrNull(memo.getCreatedBy()),
                    approverId, approver.getName(), remarks);
        } else {
            User nextApprover = approvalEngineService.getNextApprover(memo, newLevel);
            memo.setCurrentApprover(nextApprover);
            log.info("Memo {} approved at level {}, next approver: {}",
                    memo.getMemoNo(), newLevel, nextApprover.getId());

            notificationService.createNotification(
                    memo.getCreatedBy(),
                    "Memo Partially Approved",
                    "Your memo " + memo.getMemoNo() + " was approved at level " + newLevel + " by " + approver.getName(),
                    "MEMO_APPROVAL", memo.getId(), "MEMO");

            notificationService.createNotification(
                    nextApprover.getId(),
                    "Memo Pending Approval",
                    "Memo " + memo.getMemoNo() + " (" + memo.getTitle() + ") requires your approval (Level " + (newLevel + 1) + ")",
                    "MEMO_APPROVAL", memo.getId(), "MEMO");

            emailNotify(NotificationEventType.MEMO_APPROVED, memo, userOrNull(memo.getCreatedBy()),
                    approverId, approver.getName(), remarks);
            emailNotify(NotificationEventType.MEMO_SUBMITTED, memo, nextApprover,
                    approverId, approver.getName(), null);
        }

        memo = memoRepository.save(memo);
        auditEventPublisher.recordChange(approverId, ACTION_APPROVED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks));
        return memo;
    }

    public Memo rejectMemo(Long memoId, Long approverId, String remarks) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Memo is not pending approval. Current status: " + memo.getStatus());
        }

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks);

        if (workflowEngineService.hasActiveConfigForMemoType(memoTypeIdOf(memo)) || memo.getCategory() != null) {
            WorkflowStageInstance current = workflowEngineService.getCurrentStage(memoId);
            if (current != null) {
                workflowEngineService.rejectStage(memoId, current.getId(), approverId, remarks);
            }
            Memo after = memoRepository.findById(memoId).orElse(memo);
            auditEventPublisher.recordChange(approverId, ACTION_REJECTED, ENTITY_MEMO, memoId, before,
                    stateSnapshot(after.getStatus(), after.getApprovalLevel(), approverIdOf(after), remarks));
            return after;
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + approverId));

        memo.setStatus(MemoStatus.REJECTED);
        memo.setCurrentApprover(null);
        memo = memoRepository.save(memo);

        ApprovalHistory history = new ApprovalHistory();
        history.setMemo(memo);
        history.setApprover(approver);
        history.setAction(MemoStatus.REJECTED);
        history.setRemarks(remarks);
        history.setApprovalLevel(memo.getApprovalLevel() + 1);
        history.setActionDate(LocalDateTime.now());
        approvalHistoryRepository.save(history);

        log.info("Memo {} rejected by user {}", memo.getMemoNo(), approverId);

        notificationService.createNotification(
                memo.getCreatedBy(),
                "Memo Rejected",
                "Your memo " + memo.getMemoNo() + " was rejected by " + approver.getName()
                        + (remarks != null ? ": " + remarks : ""),
                "MEMO_REJECTED", memo.getId(), "MEMO");

        emailNotify(NotificationEventType.MEMO_REJECTED, memo, userOrNull(memo.getCreatedBy()),
                approverId, approver.getName(), remarks);

        auditEventPublisher.recordChange(approverId, ACTION_REJECTED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks));

        return memo;
    }

    public Memo returnMemo(Long memoId, Long approverId, String remarks) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Memo is not pending approval. Current status: " + memo.getStatus());
        }

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks);

        if (workflowEngineService.hasActiveConfigForMemoType(memoTypeIdOf(memo)) || memo.getCategory() != null) {
            WorkflowStageInstance current = workflowEngineService.getCurrentStage(memoId);
            if (current != null) {
                workflowEngineService.returnToStage(memoId, current.getId(), approverId, remarks);
            }
            Memo after = memoRepository.findById(memoId).orElse(memo);
            auditEventPublisher.recordChange(approverId, ACTION_RETURNED, ENTITY_MEMO, memoId, before,
                    stateSnapshot(after.getStatus(), after.getApprovalLevel(), approverIdOf(after), remarks));
            return after;
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + approverId));

        memo.setStatus(MemoStatus.REVISION_REQUESTED);
        memo.setCurrentApprover(null);
        memo = memoRepository.save(memo);

        ApprovalHistory history = new ApprovalHistory();
        history.setMemo(memo);
        history.setApprover(approver);
        history.setAction(MemoStatus.REVISION_REQUESTED);
        history.setRemarks(remarks);
        history.setApprovalLevel(memo.getApprovalLevel() + 1);
        history.setActionDate(LocalDateTime.now());
        approvalHistoryRepository.save(history);

        log.info("Memo {} returned for revision by user {}: {}", memo.getMemoNo(), approverId, remarks);

        emailNotify(NotificationEventType.MEMO_RETURNED, memo, userOrNull(memo.getCreatedBy()),
                approverId, approver.getName(), remarks);

        auditEventPublisher.recordChange(approverId, ACTION_RETURNED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks));

        return memo;
    }

    public Memo forwardMemo(Long memoId, Long userId, Long toUserId, String remarks) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));
        User fromUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + toUserId));

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks);

        if (memo.getBranch() != null) {
            Branch toBranch = toUser.getBranches() != null && !toUser.getBranches().isEmpty()
                    ? toUser.getBranches().get(0) : memo.getBranch();
            MemoRouting routing = new MemoRouting();
            routing.setMemo(memo);
            routing.setFromBranch(memo.getBranch());
            routing.setToBranch(toBranch);
            routing.setRoutingOrder(memoRoutingRepository.findByMemoId(memoId).size() + 1);
            routing.setStatus(MemoRoutingStatus.FORWARDED);
            routing.setRemarks(remarks);
            memoRoutingRepository.save(routing);
        }

        memo.setCurrentApprover(toUser);
        memo = memoRepository.save(memo);

        ApprovalHistory history = new ApprovalHistory();
        history.setMemo(memo);
        history.setApprover(fromUser);
        history.setAction(MemoStatus.PENDING_APPROVAL);
        history.setRemarks("Forwarded to " + toUser.getName() + ": " + remarks);
        history.setApprovalLevel(memo.getApprovalLevel());
        history.setActionDate(LocalDateTime.now());
        approvalHistoryRepository.save(history);

        log.info("Memo {} forwarded from user {} to user {}", memo.getMemoNo(), userId, toUserId);

        emailNotify(NotificationEventType.MEMO_FORWARDED, memo, toUser, userId, fromUser.getName(), remarks);

        auditEventPublisher.recordChange(userId, ACTION_FORWARDED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks));

        return memo;
    }

    public Memo backwardMemo(Long memoId, Long userId, String remarks) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks);

        List<ApprovalHistory> historyList = approvalHistoryRepository.findByMemoIdOrderByApprovalLevelAsc(memoId);
        if (historyList.isEmpty()) {
            throw new IllegalStateException("No approval history found to backtrack");
        }

        ApprovalHistory lastAction = historyList.stream()
                .max(Comparator.comparing(ApprovalHistory::getApprovalLevel)
                        .thenComparing(ApprovalHistory::getActionDate))
                .orElseThrow(() -> new RuntimeException("Unable to determine last action"));

        int previousLevel = memo.getApprovalLevel() - 1;
        if (previousLevel < 0) {
            throw new IllegalStateException("Cannot backtrack further, memo is at initial state");
        }
        memo.setApprovalLevel(previousLevel);

        User previousApprover = findApproverForLevel(historyList, previousLevel);
        if (previousApprover != null) {
            memo.setCurrentApprover(previousApprover);
        }

        memo = memoRepository.save(memo);

        ApprovalHistory history = new ApprovalHistory();
        history.setMemo(memo);
        history.setApprover(user);
        history.setAction(MemoStatus.PENDING_APPROVAL);
        history.setRemarks("Backwarded to level " + previousLevel + ": " + remarks);
        history.setApprovalLevel(previousLevel + 1);
        history.setActionDate(LocalDateTime.now());
        approvalHistoryRepository.save(history);

        log.info("Memo {} backwarded to level {} by user {}", memo.getMemoNo(), previousLevel, userId);

        if (previousApprover != null) {
            emailNotify(NotificationEventType.MEMO_SUBMITTED, memo, previousApprover, userId, user.getName(), remarks);
        }

        auditEventPublisher.recordChange(userId, ACTION_BACKWARDED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), remarks));

        return memo;
    }

    public void cancelMemo(Long memoId, Long userId) {
        Memo memo = memoRepository.findByIdForUpdate(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT memos can be cancelled. Current status: " + memo.getStatus());
        }

        if (!memo.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Only the creator can cancel the memo");
        }

        Map<String, Object> before = stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), null);

        memo.setStatus(MemoStatus.CANCELLED);
        memo.setCurrentApprover(null);
        memoRepository.save(memo);
        log.info("Memo {} cancelled by user {}", memo.getMemoNo(), userId);

        auditEventPublisher.recordChange(userId, ACTION_CANCELLED, ENTITY_MEMO, memoId, before,
                stateSnapshot(memo.getStatus(), memo.getApprovalLevel(), approverIdOf(memo), null));
    }

    public void enableBranchRouting(Long memoId, boolean enabled) {
        throw new UnsupportedOperationException("Branch routing enable/disable not yet implemented");
    }

    // ── Phase 4a: email notification publishing ──────────────────────────────────────────────

    /**
     * Publishes an email notification event for a memo lifecycle change. The event is delivered
     * after the current transaction commits (see {@code NotificationDispatchService}), so a failed
     * email can never roll back the memo operation.
     */
    private void emailNotify(NotificationEventType type, Memo memo, User recipient,
                             Long actorId, String actorName, String remarks) {
        if (memo == null || recipient == null
                || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        notificationEventPublisher.publishEvent(MemoNotificationEvent.builder()
                .eventType(type)
                .memoId(memo.getId())
                .memoNo(memo.getMemoNo())
                .memoTitle(memo.getTitle())
                .recipientId(recipient.getId())
                .recipientEmail(recipient.getEmail())
                .recipientName(recipient.getName())
                .actorId(actorId)
                .actorName(actorName)
                .remarks(remarks)
                .build());
    }

    private User userOrNull(Long id) {
        return id == null ? null : userRepository.findById(id).orElse(null);
    }

    private String nameOrNull(Long id) {
        User user = userOrNull(id);
        return user == null ? null : user.getName();
    }

    @Transactional(readOnly = true)
    public List<MemoRouting> getMemoRoutingPath(Long memoId) {
        if (!memoRepository.existsById(memoId)) {
            throw new EntityNotFoundException("Memo not found with id: " + memoId);
        }
        return memoRoutingRepository.findByMemoId(memoId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> getApprovalHistory(Long memoId) {
        if (!memoRepository.existsById(memoId)) {
            throw new EntityNotFoundException("Memo not found with id: " + memoId);
        }
        return approvalHistoryRepository.findByMemoIdOrderByApprovalLevelAsc(memoId);
    }

    public Memo checkAndEscalate(Long memoId) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getCurrentApprover() != null
                && leaveTrackerRepository.existsByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        memo.getCurrentApprover().getId(), LeaveStatus.APPROVED,
                        LocalDate.now(), LocalDate.now())) {
            User nextApprover = approvalEngineService.getNextApprover(memo, memo.getApprovalLevel());
            memo.setCurrentApprover(nextApprover);
            memo = memoRepository.save(memo);
            log.info("Memo {} escalated from user {} to user {}",
                    memo.getMemoNo(), memo.getCurrentApprover().getId(), nextApprover.getId());
        }
        return memo;
    }

    public MemoDocument addDocument(Long memoId, String fileName, String filePath, String fileType, Long fileSize) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));
        MemoDocument doc = new MemoDocument();
        doc.setMemo(memo);
        doc.setFileName(fileName);
        doc.setFilePath(filePath);
        doc.setFileType(fileType);
        doc.setFileSize(fileSize);
        doc = memoDocumentRepository.save(doc);
        log.info("Document added to memo {}: {}", memoId, fileName);
        return doc;
    }

    public void removeDocument(Long documentId) {
        MemoDocument doc = memoDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("MemoDocument not found with id: " + documentId));
        storageService.delete(doc.getFilePath());
        memoDocumentRepository.deleteById(documentId);
        log.info("Document removed: {}", documentId);
    }

    @Transactional(readOnly = true)
    public List<MemoDocument> getDocuments(Long memoId) {
        if (!memoRepository.existsById(memoId)) {
            throw new EntityNotFoundException("Memo not found with id: " + memoId);
        }
        return memoDocumentRepository.findByMemoId(memoId);
    }

    @Transactional(readOnly = true)
    public MemoDocument getDocument(Long documentId) {
        return memoDocumentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("MemoDocument not found with id: " + documentId));
    }

    /** Resolves the owning memo id of a document for authorization purposes. */
    @Transactional(readOnly = true)
    public Long getMemoIdForDocument(Long documentId) {
        MemoDocument doc = getDocument(documentId);
        return doc.getMemo() != null ? doc.getMemo().getId() : null;
    }

    /**
     * Phase 4b: unifies memo numbering. When a fiscal year is resolvable (open financial period's
     * fiscal year, else the active fiscal year) the number is a row-locked per-fiscal-year sequence
     * {@code NEXM-{fiscalYearId}-{sequence}}; otherwise a legacy global {@code NEXM-{sequence}}
     * number derived from the memo count is used as a documented fallback.
     */
    private void assignMemoNumberAndFiscalYear(Memo memo) {
        FiscalYear fiscalYear = financialPeriodService.resolveCreationFiscalYear();
        if (fiscalYear != null) {
            memo.setFiscalYear(fiscalYear);
            memo.setMemoNo(memoNumberGeneratorService.generateMemoNumber(fiscalYear.getId()));
        } else {
            log.warn("No fiscal year / open financial period configured; using legacy memo numbering");
            memo.setMemoNo(String.format("NEXM-%04d", memoRepository.count() + 1));
        }
    }

    /**
     * Phase 4c — priority resolution: explicit request value wins, then the template default, then
     * {@link Priority#MEDIUM}.
     */
    private Priority resolvePriority(CreateMemoRequest request, MemoTemplate template) {
        if (request.getPriority() != null) {
            return request.getPriority();
        }
        if (template != null && template.getDefaultPriority() != null) {
            return template.getDefaultPriority();
        }
        return Priority.MEDIUM;
    }

    @Transactional(readOnly = true)
    public List<MemoCc> getMemoCcList(Long memoId) {
        if (!memoRepository.existsById(memoId)) {
            throw new EntityNotFoundException("Memo not found with id: " + memoId);
        }
        return memoCcRepository.findByMemoId(memoId);
    }

    @Transactional(readOnly = true)
    public List<Memo> getMyCcMemos(Long userId) {
        List<MemoCc> ccList = memoCcRepository.findByUserId(userId);
        return ccList.stream().map(MemoCc::getMemo).toList();
    }

    public MemoCc acknowledgeMemo(Long memoId, Long userId) {
        MemoCc cc = memoCcRepository.findByMemoIdAndUserId(memoId, userId)
                .orElseThrow(() -> new EntityNotFoundException("CC entry not found for memo " + memoId + " and user " + userId));
        cc.setAcknowledged(true);
        cc.setAcknowledgedDate(LocalDateTime.now());
        cc = memoCcRepository.save(cc);
        log.info("Memo {} acknowledged by user {}", memoId, userId);
        return cc;
    }

    private User findApproverForLevel(List<ApprovalHistory> historyList, int level) {
        return historyList.stream()
                .filter(h -> h.getApprovalLevel() == level)
                .findFirst()
                .map(ApprovalHistory::getApprover)
                .orElse(null);
    }

    // ── Audit helpers ─────────────────────────────────────────────────────────

    static final String ENTITY_MEMO = "MEMO";
    static final String ACTION_SUBMITTED = "MEMO_SUBMITTED";
    static final String ACTION_UPDATED = "MEMO_UPDATED";
    static final String ACTION_APPROVED = "MEMO_APPROVED";
    static final String ACTION_REJECTED = "MEMO_REJECTED";
    static final String ACTION_RETURNED = "MEMO_RETURNED";
    static final String ACTION_FORWARDED = "MEMO_FORWARDED";
    static final String ACTION_BACKWARDED = "MEMO_BACKWARDED";
    static final String ACTION_CANCELLED = "MEMO_CANCELLED";

    private static Long approverIdOf(Memo memo) {
        return memo != null && memo.getCurrentApprover() != null ? memo.getCurrentApprover().getId() : null;
    }

    private static Long memoTypeIdOf(Memo memo) {
        return memo != null && memo.getMemoType() != null ? memo.getMemoType().getId() : null;
    }

    private static Map<String, Object> stateSnapshot(MemoStatus status, Integer level, Long approverId, String remarks) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status != null ? status.name() : null);
        map.put("approvalLevel", level);
        map.put("currentApproverId", approverId);
        map.put("remarks", remarks);
        return map;
    }
}
