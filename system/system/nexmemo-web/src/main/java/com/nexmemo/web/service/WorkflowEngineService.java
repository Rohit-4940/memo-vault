package com.nexmemo.web.service;

import com.nexmemo.core.entity.*;
import com.nexmemo.core.enums.*;
import com.nexmemo.core.event.MemoNotificationEvent;
import com.nexmemo.core.repository.*;
import com.nexmemo.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Workflow engine driving stage-by-stage approval.
 *
 * <p><b>Phase 5 — workflow v2:</b></p>
 * <ul>
 *   <li><b>Config-driven routing.</b> When the memo's memo type has an active workflow config
 *       that defines stages, stage instances are built from those configured stages instead of the
 *       legacy global rule-matrix stages.</li>
 *   <li><b>Assignee pool.</b> Every eligible approver of a stage may act on it, not only the
 *       primary assignee. The pool is resolved at submission time and frozen onto the instance
 *       (comma-separated {@code assignee_ids}), so a pool member approving a middle stage works
 *       exactly like the designated final approver approving the last one.</li>
 *   <li><b>Config revision freeze.</b> Stage instances snapshot the config id, config revision,
 *       stage name, and guard flags at submission. Re-saving the config or its stages later never
 *       rewrites in-progress instances — decisions are always evaluated against the captured
 *       snapshot.</li>
 *   <li><b>Per-stage guards.</b> Stages that require a comment or a document enforce it at
 *       decision time, evaluated from the frozen snapshot.</li>
 *   <li><b>Concurrency safety.</b> Decisions lock the stage instance pessimistically so two
 *       eligible pool members approving simultaneously cannot double-advance the memo.</li>
 * </ul>
 */
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
    private final ApplicationEventPublisher notificationEventPublisher;
    private final MemoWorkflowConfigRepository memoWorkflowConfigRepository;
    private final MemoWorkflowStageRepository memoWorkflowStageRepository;

    // ── Configuration capability (Phase 5) ────────────────────────────────────

    /**
     * True when the memo type has an active workflow config that actually defines stages. Used by
     * {@link MemoService} to route submissions and decisions toward the config-driven path.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveConfigForMemoType(Long memoTypeId) {
        if (memoTypeId == null) {
            return false;
        }
        return memoWorkflowConfigRepository.findByMemoTypeId(memoTypeId)
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .map(c -> !memoWorkflowStageRepository.findByConfigIdOrderByStageOrderAsc(c.getId()).isEmpty())
                .orElse(false);
    }

    /**
     * The frozen assignee pool of a stage instance — every eligible approver of the stage.
     * Exposed read-only so clients can show eligibility hints.
     */
    @Transactional(readOnly = true)
    public List<Long> getEligibleAssigneesForStage(Long memoId, Long stageOrInstanceId) {
        WorkflowStageInstance instance = findStageInstance(memoId, stageOrInstanceId);
        List<Long> ids = new ArrayList<>();
        if (instance.getAssignee() != null) {
            ids.add(instance.getAssignee().getId());
        }
        if (instance.getAssigneeIds() != null && !instance.getAssigneeIds().isBlank()) {
            for (String part : instance.getAssigneeIds().split(",")) {
                try {
                    long v = Long.parseLong(part.trim());
                    if (!ids.contains(v)) {
                        ids.add(v);
                    }
                } catch (NumberFormatException ignored) {
                    // skip malformed ids
                }
            }
        }
        return ids;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

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

        initInstancesFor(memo);

        notificationService.createNotification(
                memo.getCreatedBy(), "Memo Submitted",
                "Your memo " + memo.getMemoNo() + " has been submitted for approval",
                "MEMO_SUBMITTED", memo.getId(), "MEMO");

        WorkflowStageInstance firstStage = getCurrentStage(memo.getId());
        if (firstStage != null && firstStage.getAssignee() != null) {
            emailNotify(NotificationEventType.MEMO_SUBMITTED, memo, firstStage.getAssignee(),
                    userId, nameOrNull(userId), null);
        }

        return memo;
    }

    /**
     * Builds the frozen stage instances for a submitted memo. Prefers the memo type's active
     * workflow config (Phase 5); falls back to the legacy global-stage path for category-driven
     * memos; otherwise the memo keeps no stage instances (legacy level-based approval path).
     */
    private void initInstancesFor(Memo memo) {
        MemoWorkflowConfig config = null;
        List<MemoWorkflowStage> configStages = List.of();
        Long memoTypeId = memo.getMemoType() != null ? memo.getMemoType().getId() : null;
        if (memoTypeId != null) {
            config = memoWorkflowConfigRepository.findByMemoTypeId(memoTypeId)
                    .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                    .orElse(null);
            if (config != null) {
                configStages = memoWorkflowStageRepository.findByConfigIdOrderByStageOrderAsc(config.getId());
                if (configStages.isEmpty()) {
                    log.warn("Workflow config {} for memo type {} defines no stages; falling back to legacy path",
                            config.getId(), memoTypeId);
                    config = null;
                }
            }
        }
        if (config != null) {
            initStageInstancesFromConfig(memo, config, configStages);
        } else if (memo.getCategory() != null) {
            initStageInstances(memo);
        }
    }

    /** Legacy path: one instance per global {@code workflow_stage}, assignee from the rule matrix. */
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
            instance.setStageName(stage.getName());
            instance.setRequireComment(stage.getRequireComment());
            instance.setRequireDocument(stage.getRequireDocument());
            instance.setStatus(i == 0 ? WorkflowStageStatus.IN_PROGRESS : WorkflowStageStatus.DRAFT);
            if (i == 0) {
                instance.setEnteredAt(LocalDateTime.now());
                if (stage.getSlaHours() != null && stage.getSlaHours() > 0) {
                    instance.setSlaDeadline(LocalDateTime.now().plusHours(stage.getSlaHours()));
                }
                resolveAssignee(instance, stage);
                if (instance.getAssignee() != null) {
                    instance.setAssigneeIds(String.valueOf(instance.getAssignee().getId()));
                }
            }
            stageInstanceRepository.save(instance);
            log.debug("Created stage instance {} for memo {} stage {}", instance.getId(), memo.getId(), stage.getName());
        }
    }

    /**
     * Phase 5 path: one instance per configured stage, built from the config at submission time.
     * Everything an in-flight decision needs (config id, config revision, stage name, assignee
     * pool, guard flags) is captured here and never re-read from the config afterwards.
     */
    private void initStageInstancesFromConfig(Memo memo, MemoWorkflowConfig config,
                                              List<MemoWorkflowStage> configStages) {
        boolean requireDocument = Boolean.TRUE.equals(config.getRequireDocumentVerification());
        Integer slaHours = config.getSlaHours();
        for (int i = 0; i < configStages.size(); i++) {
            MemoWorkflowStage cfgStage = configStages.get(i);
            WorkflowStageInstance instance = new WorkflowStageInstance();
            instance.setMemo(memo);
            instance.setStageName(cfgStage.getStageName());
            instance.setConfigId(config.getId());
            instance.setConfigVersion(config.getRevision());
            // Per-stage guards (Phase 6c), falling back to the previous defaults when a stage has
            // not been given explicit values: comment defaults on, document inherits the
            // config-level document-verification requirement.
            instance.setRequireComment(cfgStage.getRequireComment() != null
                    ? cfgStage.getRequireComment() : Boolean.TRUE);
            instance.setRequireDocument(cfgStage.getRequireDocument() != null
                    ? cfgStage.getRequireDocument() : requireDocument);
            instance.setStatus(i == 0 ? WorkflowStageStatus.IN_PROGRESS : WorkflowStageStatus.DRAFT);
            if (slaHours != null && slaHours > 0) {
                instance.setSlaDeadline(LocalDateTime.now().plusHours(slaHours));
            }
            if (i == 0) {
                instance.setEnteredAt(LocalDateTime.now());
                resolvePool(instance, cfgStage, memo);
            }
            stageInstanceRepository.save(instance);
            log.debug("Created config stage instance {} for memo {} stage {} (config revision {})",
                    instance.getId(), memo.getId(), cfgStage.getStageName(), config.getRevision());
        }
    }

    /** Resolves and freezes the assignee pool of the first configured stage. */
    private void resolvePool(WorkflowStageInstance instance, MemoWorkflowStage cfgStage, Memo memo) {
        List<Long> pool = new ArrayList<>();
        String assigneeType = cfgStage.getAssigneeType();
        Long assigneeId = cfgStage.getAssigneeId();
        if ("ROLE".equalsIgnoreCase(assigneeType) && assigneeId != null) {
            // Every member of the role is an eligible approver for this stage.
            userRepository.findByRoleId(assigneeId).forEach(u -> pool.add(u.getId()));
        } else if (assigneeId != null) {
            // USER (or legacy/blank type with an explicit assignee): single eligible approver.
            pool.add(assigneeId);
        }
        if (pool.isEmpty()) {
            pool.addAll(resolveMatrixPool(memo));
        }
        List<Long> distinct = pool.stream().distinct().toList();
        instance.setAssigneeIds(distinct.isEmpty()
                ? null : String.join(",", distinct.stream().map(String::valueOf).toList()));
        instance.setAssignee(distinct.isEmpty() ? null : userRepository.findById(distinct.get(0)).orElse(null));
    }

    /** Fallback pool from the category rule matrix (all direct users + all role members). */
    private List<Long> resolveMatrixPool(Memo memo) {
        List<Long> pool = new ArrayList<>();
        Long categoryId = memo.getCategory() != null ? memo.getCategory().getId() : null;
        if (categoryId == null) {
            return pool;
        }
        for (ApprovalRuleMatrix rule : approvalRuleMatrixRepository.findByCategoryIdOrderByPriorityAsc(categoryId)) {
            if (rule.getUserId() != null) {
                pool.add(rule.getUserId());
            }
            if (rule.getRoleId() != null) {
                userRepository.findByRoleId(rule.getRoleId()).forEach(u -> pool.add(u.getId()));
            }
        }
        return pool;
    }

    /** Legacy single-assignee resolution from the category + stage rule matrix. */
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

    // ── Stage decisions (Phase 5: pool eligibility + frozen guards) ───────────

    public WorkflowStageInstance approveStage(Long memoId, Long stageOrInstanceId, Long userId, String comment) {
        WorkflowStageInstance instance = decideStage(memoId, stageOrInstanceId, userId, comment, true, true);

        instance.setStatus(WorkflowStageStatus.APPROVED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setComment(comment);
        instance.setActionTaken("APPROVED");
        instance.setActionBy(userId);
        stageInstanceRepository.save(instance);

        advanceToNextStage(memoId);
        return instance;
    }

    public WorkflowStageInstance rejectStage(Long memoId, Long stageOrInstanceId, Long userId, String comment) {
        WorkflowStageInstance instance = decideStage(memoId, stageOrInstanceId, userId, comment, true, false);

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
                "Your memo " + memo.getMemoNo() + " was rejected at stage " + stageLabel(instance),
                "MEMO_REJECTED", memo.getId(), "MEMO");

        emailNotify(NotificationEventType.MEMO_REJECTED, memo, userOrNull(memo.getCreatedBy()),
                userId, nameOrNull(userId), comment);

        return instance;
    }

    public WorkflowStageInstance returnToStage(Long memoId, Long stageOrInstanceId, Long userId, String comment) {
        WorkflowStageInstance instance = decideStage(memoId, stageOrInstanceId, userId, comment, true, false);

        instance.setStatus(WorkflowStageStatus.RETURNED);
        instance.setCompletedAt(LocalDateTime.now());
        instance.setComment(comment);
        instance.setActionTaken("RETURNED");
        instance.setActionBy(userId);
        stageInstanceRepository.save(instance);

        Memo memo = instance.getMemo();
        memo.setStatus(MemoStatus.REVISION_REQUESTED);
        memoRepository.save(memo);

        emailNotify(NotificationEventType.MEMO_RETURNED, memo, userOrNull(memo.getCreatedBy()),
                userId, nameOrNull(userId), comment);

        return instance;
    }

    /**
     * Common guard for any stage decision made by an eligible pool member: the instance must be
     * the in-progress stage, the caller must belong to the frozen assignee pool, and configured
     * guards (comment / document) must be satisfied.
     */
    private WorkflowStageInstance decideStage(Long memoId, Long stageOrInstanceId, Long userId,
                                              String comment, boolean requireDecisionComment,
                                              boolean requireDecisionDocuments) {
        WorkflowStageInstance instance = findActionableInstance(memoId, stageOrInstanceId);
        if (instance.getStatus() != WorkflowStageStatus.IN_PROGRESS) {
            throw new IllegalStateException("Stage is not in progress: " + stageLabel(instance));
        }
        if (!isEligible(instance, userId)) {
            throw new IllegalStateException("You are not an eligible approver for this stage");
        }
        if (requireDecisionComment) {
            checkCommentRequired(instance, comment);
        }
        if (requireDecisionDocuments) {
            checkDocumentRequired(instance);
        }
        return instance;
    }

    public WorkflowStageInstance delegateStage(Long memoId, Long stageOrInstanceId, Long fromUserId, Long toUserId) {
        WorkflowStageInstance instance = findActionableInstance(memoId, stageOrInstanceId);
        if (instance.getStatus() != WorkflowStageStatus.IN_PROGRESS) {
            throw new IllegalStateException("Stage is not in progress: " + stageLabel(instance));
        }
        if (!isEligible(instance, fromUserId)) {
            throw new IllegalStateException("You are not an eligible approver for this stage");
        }
        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + toUserId));
        if (fromUserId.equals(toUserId)) {
            throw new IllegalStateException("Cannot delegate a stage to yourself");
        }
        // Delegation narrows the frozen pool to the delegated user only.
        instance.setAssignee(toUser);
        instance.setAssigneeIds(String.valueOf(toUserId));
        instance.setActionTaken("DELEGATED");
        instance.setActionBy(fromUserId);
        stageInstanceRepository.save(instance);

        emailNotify(NotificationEventType.MEMO_DELEGATED, instance.getMemo(), toUser,
                fromUserId, nameOrNull(fromUserId), null);

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
                if (instance.getSlaDeadline() == null) {
                    Integer sla = instance.getStage() != null ? instance.getStage().getSlaHours() : null;
                    if (sla != null && sla > 0) {
                        instance.setSlaDeadline(LocalDateTime.now().plusHours(sla));
                    }
                }
                // Legacy instances resolve their assignee when activated; config-driven instances
                // were already frozen with a full pool, so their assignee must not change.
                if (instance.getStage() != null && instance.getAssignee() == null) {
                    resolveAssignee(instance, instance.getStage());
                    if (instance.getAssignee() != null) {
                        instance.setAssigneeIds(String.valueOf(instance.getAssignee().getId()));
                    }
                }
                stageInstanceRepository.save(instance);
                foundInProgress = true;

                if (instance.getAssignee() != null) {
                    emailNotify(NotificationEventType.MEMO_SUBMITTED, instance.getMemo(),
                            instance.getAssignee(), null, null, null);
                }
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

            emailNotify(NotificationEventType.MEMO_APPROVED, memo, userOrNull(memo.getCreatedBy()),
                    null, null, null);
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

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * Resolves the stage instance a decision applies to. Accepts either the workflow-stage id
     * (legacy clients passing {@code stage.id}) or the stage-instance id (preferred, unambiguous
     * for config-driven instances), locking the row so concurrent pool members cannot both decide.
     */
    private WorkflowStageInstance findActionableInstance(Long memoId, Long stageOrInstanceId) {
        return stageInstanceRepository.findByIdForUpdate(stageOrInstanceId)
                .filter(i -> i.getMemo() != null && Objects.equals(i.getMemo().getId(), memoId))
                .or(() -> stageInstanceRepository.findByMemoIdAndStageId(memoId, stageOrInstanceId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Workflow stage instance not found for memo " + memoId));
    }

    /** Non-locking lookup used by read-only helpers. */
    private WorkflowStageInstance findStageInstance(Long memoId, Long stageOrInstanceId) {
        return stageInstanceRepository.findById(stageOrInstanceId)
                .filter(i -> i.getMemo() != null && Objects.equals(i.getMemo().getId(), memoId))
                .or(() -> stageInstanceRepository.findByMemoIdAndStageId(memoId, stageOrInstanceId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Workflow stage instance not found for memo " + memoId));
    }

    /** May this user act on the stage? Primary assignee or any member of the frozen pool. */
    private boolean isEligible(WorkflowStageInstance instance, Long userId) {
        if (userId == null) {
            return false;
        }
        if (instance.getAssignee() != null && Objects.equals(instance.getAssignee().getId(), userId)) {
            return true;
        }
        String ids = instance.getAssigneeIds();
        if (ids == null || ids.isBlank()) {
            return false;
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .anyMatch(s -> s.equals(String.valueOf(userId)));
    }

    /** Effective require-comment flag from the frozen snapshot, falling back to the global stage. */
    private void checkCommentRequired(WorkflowStageInstance instance, String comment) {
        Boolean required = instance.getRequireComment();
        if (required == null && instance.getStage() != null) {
            required = instance.getStage().getRequireComment();
        }
        if (Boolean.TRUE.equals(required) && (comment == null || comment.isBlank())) {
            throw new IllegalStateException(
                    "A comment is required before you may decide this stage (" + stageLabel(instance) + ")");
        }
    }

    /** Effective require-document flag from the frozen snapshot, falling back to the global stage. */
    private void checkDocumentRequired(WorkflowStageInstance instance) {
        Boolean required = instance.getRequireDocument();
        if (required == null && instance.getStage() != null) {
            required = instance.getStage().getRequireDocument();
        }
        if (Boolean.TRUE.equals(required)
                && (instance.getMemo().getDocuments() == null || instance.getMemo().getDocuments().isEmpty())) {
            throw new IllegalStateException(
                    "At least one document must be attached before this stage (" + stageLabel(instance) + ") can be decided");
        }
    }

    private String stageLabel(WorkflowStageInstance instance) {
        if (instance.getStageName() != null && !instance.getStageName().isBlank()) {
            return instance.getStageName();
        }
        if (instance.getStage() != null && instance.getStage().getName() != null) {
            return instance.getStage().getName();
        }
        return "#" + instance.getId();
    }

    // ── Phase 4a: email notification publishing ──────────────────────────────

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
}