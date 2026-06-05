package com.nexmemo.web.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.enums.LeaveStatus;
import com.nexmemo.core.enums.MemoRoutingStatus;
import com.nexmemo.core.enums.MemoStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    @Data
    public static class CreateMemoRequest {
        @NotBlank
        private String title;
        private String description;
        private String content;
        private Long memoTypeId;
        private Long departmentId;
        private Long branchId;
        @NotNull
        private Priority priority;
        private BigDecimal amount;
        private Long templateId;
        private List<Long> ccUserIds;
        private Long categoryId;
    }

    @Transactional(readOnly = true)
    public List<Memo> getAllMemos() {
        return memoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<Memo> getAllMemosPaginated(Pageable pageable) {
        return memoRepository.findAllActive(pageable);
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
    public List<Memo> getMemosByStatus(MemoStatus status) {
        return memoRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Memo> getMemosByBranch(Long branchId) {
        if (!branchRepository.existsById(branchId)) {
            throw new EntityNotFoundException("Branch not found with id: " + branchId);
        }
        return memoRepository.findByBranchId(branchId);
    }

    @Transactional(readOnly = true)
    public List<Memo> searchMemos(String keyword) {
        return memoRepository.findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase(keyword);
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

        String templateContent = null;
        if (request.getTemplateId() != null) {
            templateContent = memoTemplateRepository.findById(request.getTemplateId())
                    .map(MemoTemplate::getContent).orElse(null);
            log.debug("Template {} content loaded for memo creation", request.getTemplateId());
        }

        MemoCategory category = null;
        if (request.getCategoryId() != null) {
            category = memoCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoCategory not found: " + request.getCategoryId()));
        }

        Memo memo = new Memo();
        memo.setTitle(request.getTitle());
        memo.setDescription(request.getDescription());
        memo.setContent(request.getContent() != null ? request.getContent() : templateContent);
        memo.setMemoNo(generateMemoNo(null));
        memo.setStatus(MemoStatus.DRAFT);
        memo.setPriority(request.getPriority() != null ? request.getPriority() : Priority.MEDIUM);
        memo.setMemoType(memoType);
        memo.setDepartment(department);
        memo.setBranch(branch);
        memo.setCategory(category);
        memo.setAmount(request.getAmount());

        memo = memoRepository.save(memo);
        log.info("Memo created: id={} memoNo={} by userId={}", memo.getId(), memo.getMemoNo(), creatorId);
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

    public Memo submitMemo(Long memoId, Long userId) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.DRAFT && memo.getStatus() != MemoStatus.REVISION_REQUESTED) {
            throw new IllegalStateException("Only DRAFT or REVISION_REQUESTED memos can be submitted. Current status: " + memo.getStatus());
        }

        if (!memo.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Only the creator can submit the memo");
        }

        if (memo.getCategory() != null) {
            return workflowEngineService.submitMemo(memo, userId);
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

        return memo;
    }

    public Memo approveMemo(Long memoId, Long approverId, String remarks) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Memo is not pending approval. Current status: " + memo.getStatus());
        }

        if (memo.getCategory() != null) {
            WorkflowStageInstance current = workflowEngineService.getCurrentStage(memoId);
            if (current != null) {
                workflowEngineService.approveStage(memoId, current.getStage().getId(), approverId, remarks);
            }
            return memoRepository.findById(memoId).orElse(memo);
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
        }

        memo = memoRepository.save(memo);
        return memo;
    }

    public Memo rejectMemo(Long memoId, Long approverId, String remarks) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Memo is not pending approval. Current status: " + memo.getStatus());
        }

        if (memo.getCategory() != null) {
            WorkflowStageInstance current = workflowEngineService.getCurrentStage(memoId);
            if (current != null) {
                workflowEngineService.rejectStage(memoId, current.getStage().getId(), approverId, remarks);
            }
            return memoRepository.findById(memoId).orElse(memo);
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

        return memo;
    }

    public Memo returnMemo(Long memoId, Long approverId, String remarks) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Memo is not pending approval. Current status: " + memo.getStatus());
        }

        if (memo.getCategory() != null) {
            WorkflowStageInstance current = workflowEngineService.getCurrentStage(memoId);
            if (current != null) {
                workflowEngineService.returnToStage(memoId, current.getStage().getId(), approverId, remarks);
            }
            return memoRepository.findById(memoId).orElse(memo);
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
        return memo;
    }

    public Memo forwardMemo(Long memoId, Long userId, Long toUserId, String remarks) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));
        User fromUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + toUserId));

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
        return memo;
    }

    public Memo backwardMemo(Long memoId, Long userId, String remarks) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

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
        return memo;
    }

    public void cancelMemo(Long memoId, Long userId) {
        Memo memo = memoRepository.findById(memoId)
                .orElseThrow(() -> new EntityNotFoundException("Memo not found with id: " + memoId));

        if (memo.getStatus() != MemoStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT memos can be cancelled. Current status: " + memo.getStatus());
        }

        if (!memo.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Only the creator can cancel the memo");
        }

        memo.setStatus(MemoStatus.CANCELLED);
        memo.setCurrentApprover(null);
        memoRepository.save(memo);
        log.info("Memo {} cancelled by user {}", memo.getMemoNo(), userId);
    }

    public void enableBranchRouting(Long memoId, boolean enabled) {
        throw new UnsupportedOperationException("Branch routing enable/disable not yet implemented");
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

    public String generateMemoNo(Long fiscalYearId) {
        long sequence = 1;
        Memo lastMemo = memoRepository.findTopByOrderByIdDesc().orElse(null);
        if (lastMemo != null && lastMemo.getMemoNo() != null) {
            String lastNo = lastMemo.getMemoNo();
            int lastDash = lastNo.lastIndexOf('-');
            if (lastDash > 0) {
                try {
                    sequence = Long.parseLong(lastNo.substring(lastDash + 1)) + 1;
                } catch (NumberFormatException e) {
                    sequence = memoRepository.count() + 1;
                }
            }
        }
        if (fiscalYearId == null) {
            return String.format("NEXM-%04d", sequence);
        }
        return String.format("NEXM-%d-%04d", fiscalYearId, sequence);
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
}
