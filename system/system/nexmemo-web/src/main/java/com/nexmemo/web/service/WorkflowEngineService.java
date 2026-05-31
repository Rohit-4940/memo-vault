package com.nexmemo.web.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.enums.*;
import com.nexmemo.core.repository.*;
import com.nexmemo.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final WorkflowStageRepository workflowStageRepository;
    private final WorkflowStageInstanceRepository stageInstanceRepository;
    private final ApprovalRuleMatrixRepository approvalRuleMatrixRepository;
    private final MemoCategoryRepository memoCategoryRepository;
    private final MemoRepository memoRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MemoNumberGeneratorService memoNumberGeneratorService;
    private final EscalationService escalationService;

    public Memo submitMemo(Memo memo, Long userId) {
        if (memo.getStatus() != MemoStatus.DRAFT && memo.getStatus() != MemoStatus.REVISION_REQUESTED) {
            throw new IllegalStateException("Only DRAFT or REVISION_REQUESTED memos can be submitted");
        }
        if (!memo.getCreatedBy().equals(userId)) {
            throw new IllegalStateException("Only the creator can submit the memo");
        }

        memo.setStatus(MemoStatus.PENDING_APPROVAL);
        memo.setSubmittedDate(LocalDateTime.now());
        memo = memoRepository.save(memo);

        if (memo.getCategory() != null) {
            initStageInstances(memo);
        }

        notificationService.createNotification(
                memo.getCreatedBy(), "Memo Submitted",
                "Your memo " + memo.getMemoNo() + " has been submitted for approval",
                "MEMO_SUBMITTED", memo.getId(), "MEMO");

        return memo;
    }

    private void initStageInstances(Memo memo) {
        List<WorkflowStage> stages = workflowStageRepository.findAllByOrderByOrderIndexAsc();
        if (stages.isEmpty()) {
            log.warn("No workflow stages defined; skipping stage instance creation for memo {}", memo.getId());
            return;
        }

        for (int i = 0; i < stages.size(); i++) {
            WorkflowStage stage = stages.get(i);
            WorkflowStageInstance instance = new WorkflowStageInstance();
            instance.setMemo(memo);
            instance.setStage(stage);
            instance.setStatus(i == 0 ? WorkflowStageStatus.IN_PROGRESS : WorkflowStageStatus.DRAFT);
            if (i == 0) {
                instance.setEnteredAt(LocalDateTime.now());
                if (stage.getSlaHours() != null && stage.getSlaHours() > 0) {
                    instance.setSlaDeadline(LocalDateTime.now().plusHours(stage.getSlaHours()));
                }
                resolveAssignee(instance, stage);
            }
            stageInstanceRepository.save(instance);
            log.debug("Created stage instance {} for memo {} stage {}", instance.getId(), memo.getId(), stage.getName());
        }
    }

    private void resolveAssignee(WorkflowStageInstance instance, WorkflowStage stage) {
        Long categoryId = instance.getMemo().getCategory() != null
                ? instance.getMemo().getCategory().getId() : null;

        if (categoryId != null) {
            List<ApprovalRuleMatrix> rules = approvalRuleMatrixRepository
                    .findByCategoryIdAndStageIdOrderByPriorityAsc(categoryId, stage.getId());
            for (ApprovalRuleMatrix rule : rules) {
                if (rule.getUserId() != null) {
                    User user = userRepository.findById(rule.getUserId()).orElse(null);
                    if (user != null) {
                        instance.setAssignee(user);
                        return;
                    }
                }
                if (rule.getRoleId() != null) {
                    List<User> roleUsers = userRepository.findByRoleId(rule.getRoleId());
                    if (!roleUsers.isEmpty()) {
                        instance.setAssignee(roleUsers.get(0));
                        return;
                    }
                }
            }
        }
    }

    public WorkflowStageInstance approveStage(Long memoId, Long stageId, Long userId, String comment) {
        WorkflowStageInstance instance = stageInstanceRepository.findByMemoIdAndStageId(memoId, stageId)
                .orElseThrow(() -> new EntityNotFoundException("Stage instance not found"));

        if (instance.getStatus() != WorkflowStageStatus.IN_PROGRESS) {
            throw new IllegalStateException("Stage is not in progress");
        }

        if (!instance.getAssignee().getId().equals(userId)) {
            throw new IllegalStateException("You are not the assigned approver for this stage");
        }

        instance.setStatus(WorkflowStageStatus.APPROVED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setComment(comment);
        instance.setActionTaken("APPROVED");
        instance.setActionBy(userId);
        stageInstanceRepository.save(instance);

        advanceToNextStage(memoId);
        return instance;
    }

    public WorkflowStageInstance rejectStage(Long memoId, Long stageId, Long userId, String comment) {
        WorkflowStageInstance instance = stageInstanceRepository.findByMemoIdAndStageId(memoId, stageId)
                .orElseThrow(() -> new EntityNotFoundException("Stage instance not found"));

        if (instance.getStatus() != WorkflowStageStatus.IN_PROGRESS) {
            throw new IllegalStateException("Stage is not in progress");
        }

        instance.setStatus(WorkflowStageStatus.REJECTED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setComment(comment);
        instance.setActionTaken("REJECTED");
        instance.setActionBy(userId);
        stageInstanceRepository.save(instance);

        Memo memo = instance.getMemo();
        memo.setStatus(MemoStatus.REJECTED);
        memoRepository.save(memo);

        notificationService.createNotification(
                memo.getCreatedBy(), "Memo Rejected",
                "Your memo " + memo.getMemoNo() + " was rejected at stage " + instance.getStage().getName(),
                "MEMO_REJECTED", memo.getId(), "MEMO");

        return instance;
    }

    public WorkflowStageInstance returnToStage(Long memoId, Long stageId, Long userId, String comment) {
        WorkflowStageInstance instance = stageInstanceRepository.findByMemoIdAndStageId(memoId, stageId)
                .orElseThrow(() -> new EntityNotFoundException("Stage instance not found"));

        instance.setStatus(WorkflowStageStatus.RETURNED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setComment(comment);
        instance.setActionTaken("RETURNED");
        instance.setActionBy(userId);
        stageInstanceRepository.save(instance);

        Memo memo = instance.getMemo();
        memo.setStatus(MemoStatus.REVISION_REQUESTED);
        memoRepository.save(memo);

        return instance;
    }

    private void advanceToNextStage(Long memoId) {
        List<WorkflowStageInstance> instances = stageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(memoId);
        boolean foundInProgress = false;

        for (WorkflowStageInstance instance : instances) {
            if (instance.getStatus() == WorkflowStageStatus.IN_PROGRESS) {
                foundInProgress = true;
                break;
            }
            if (instance.getStatus() == WorkflowStageStatus.DRAFT) {
                instance.setStatus(WorkflowStageStatus.IN_PROGRESS);
                instance.setEnteredAt(LocalDateTime.now());
                if (instance.getStage().getSlaHours() != null && instance.getStage().getSlaHours() > 0) {
                    instance.setSlaDeadline(LocalDateTime.now().plusHours(instance.getStage().getSlaHours()));
                }
                resolveAssignee(instance, instance.getStage());
                stageInstanceRepository.save(instance);
                foundInProgress = true;
                break;
            }
        }

        if (!foundInProgress) {
            Memo memo = memoRepository.findById(memoId)
                    .orElseThrow(() -> new EntityNotFoundException("Memo not found: " + memoId));
            memo.setStatus(MemoStatus.APPROVED);
            memo.setApprovedDate(LocalDateTime.now());
            memoRepository.save(memo);

            notificationService.createNotification(
                    memo.getCreatedBy(), "Memo Approved",
                    "Your memo " + memo.getMemoNo() + " has been fully approved",
                    "MEMO_APPROVED", memo.getId(), "MEMO");
        }
    }

    public WorkflowStageInstance getCurrentStage(Long memoId) {
        List<WorkflowStageInstance> instances = stageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(memoId);
        return instances.stream()
                .filter(i -> i.getStatus() == WorkflowStageStatus.IN_PROGRESS)
                .findFirst()
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkflowStageInstance> getStageInstances(Long memoId) {
        return stageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(memoId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowStage> getAllStages() {
        return workflowStageRepository.findAllByOrderByOrderIndexAsc();
    }

    public WorkflowStage createStage(WorkflowStage stage) {
        return workflowStageRepository.save(stage);
    }

    public WorkflowStage updateStage(Long id, WorkflowStage updated) {
        WorkflowStage stage = workflowStageRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("WorkflowStage not found: " + id));
        stage.setName(updated.getName());
        stage.setOrderIndex(updated.getOrderIndex());
        stage.setColor(updated.getColor());
        stage.setIcon(updated.getIcon());
        stage.setSlaHours(updated.getSlaHours());
        stage.setRequireComment(updated.getRequireComment());
        stage.setRequireDocument(updated.getRequireDocument());
        stage.setEnableEscalation(updated.getEnableEscalation());
        stage.setNotifyEmail(updated.getNotifyEmail());
        stage.setNotifySms(updated.getNotifySms());
        stage.setNotifyInApp(updated.getNotifyInApp());
        return workflowStageRepository.save(stage);
    }

    public void deleteStage(Long id) {
        workflowStageRepository.deleteById(id);
    }

    public void reorderStages(List<Long> stageIds) {
        for (int i = 0; i < stageIds.size(); i++) {
            final int orderIndex = i;
            workflowStageRepository.findById(stageIds.get(i)).ifPresent(stage -> {
                stage.setOrderIndex(orderIndex);
                workflowStageRepository.save(stage);
            });
        }
    }

    public WorkflowStageInstance delegateStage(Long memoId, Long stageId, Long fromUserId, Long toUserId) {
        WorkflowStageInstance instance = stageInstanceRepository.findByMemoIdAndStageId(memoId, stageId)
                .orElseThrow(() -> new EntityNotFoundException("Stage instance not found"));

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + toUserId));
        instance.setAssignee(toUser);
        instance.setActionTaken("DELEGATED");
        instance.setActionBy(fromUserId);
        stageInstanceRepository.save(instance);
        return instance;
    }
}
