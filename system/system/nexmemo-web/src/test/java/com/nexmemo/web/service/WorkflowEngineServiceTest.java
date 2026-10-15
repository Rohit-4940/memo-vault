package com.nexmemo.web.service;

import com.nexmemo.core.entity.ApprovalRuleMatrix;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoCategory;
import com.nexmemo.core.entity.MemoType;
import com.nexmemo.core.entity.MemoWorkflowConfig;
import com.nexmemo.core.entity.MemoWorkflowStage;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.entity.WorkflowStage;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.WorkflowStageStatus;
import com.nexmemo.core.repository.ApprovalRuleMatrixRepository;
import com.nexmemo.core.repository.MemoCategoryRepository;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.MemoWorkflowConfigRepository;
import com.nexmemo.core.repository.MemoWorkflowStageRepository;
import com.nexmemo.core.repository.WorkflowStageInstanceRepository;
import com.nexmemo.core.repository.WorkflowStageRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5 (workflow v2) tests: config-driven routing, frozen in-flight snapshots, assignee pools
 * (every eligible middle approver can act), per-stage guards, delegation and concurrency-safe
 * decision path.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceTest {

    @Mock private WorkflowStageRepository workflowStageRepository;
    @Mock private WorkflowStageInstanceRepository stageInstanceRepository;
    @Mock private ApprovalRuleMatrixRepository approvalRuleMatrixRepository;
    @Mock private MemoCategoryRepository memoCategoryRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private MemoNumberGeneratorService memoNumberGeneratorService;
    @Mock private EscalationService escalationService;
    @Mock private ApplicationEventPublisher notificationEventPublisher;
    @Mock private MemoWorkflowConfigRepository memoWorkflowConfigRepository;
    @Mock private MemoWorkflowStageRepository memoWorkflowStageRepository;

    @InjectMocks private WorkflowEngineService engine;

    private static final Long MEMO_ID = 100L;
    private static final Long CREATOR = 10L;

    // ── builders ──────────────────────────────────────────────────────────────

    private Memo memo(Long creator, MemoStatus status) {
        Memo m = new Memo();
        m.setId(MEMO_ID);
        m.setCreatedBy(creator);
        m.setStatus(status);
        m.setMemoNo("NEXM-1-0001");
        m.setTitle("Travel reimbursement");
        return m;
    }

    private Memo memoWithType(Long memoTypeId) {
        Memo m = memo(CREATOR, MemoStatus.DRAFT);
        MemoType type = new MemoType();
        type.setId(memoTypeId);
        m.setMemoType(type);
        return m;
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setName("User " + id);
        return u;
    }

    private MemoWorkflowConfig config(Long id, int revision, boolean active) {
        MemoWorkflowConfig c = new MemoWorkflowConfig();
        c.setId(id);
        c.setMemoTypeId(5L);
        c.setRevision(revision);
        c.setIsActive(active);
        c.setRequireDocumentVerification(false);
        return c;
    }

    private MemoWorkflowStage cfgStage(int order, String name, String type, Long assigneeId) {
        MemoWorkflowStage s = new MemoWorkflowStage();
        s.setConfigId(50L);
        s.setStageOrder(order);
        s.setStageName(name);
        s.setAssigneeType(type);
        s.setAssigneeId(assigneeId);
        return s;
    }

    private WorkflowStageInstance instance(Long id, Long memoId, WorkflowStageStatus status,
                                           String assigneeIds, Long primaryId) {
        WorkflowStageInstance i = new WorkflowStageInstance();
        i.setId(id);
        i.setMemo(memo(CREATOR, MemoStatus.PENDING_APPROVAL));
        i.getMemo().setId(memoId);
        i.setStatus(status);
        i.setAssigneeIds(assigneeIds);
        i.setRequireComment(true);
        i.setRequireDocument(false);
        if (primaryId != null) {
            i.setAssignee(user(primaryId));
        }
        return i;
    }

    // ── submit: config-driven, frozen snapshot + role pool ────────────────────

    @Test
    void submitMemo_configDriven_freezesSnapshotAndResolvesRolePool() {
        Memo m = memoWithType(5L);
        when(memoRepository.save(any(Memo.class))).thenAnswer(inv -> inv.getArgument(0));
        when(memoWorkflowConfigRepository.findByMemoTypeId(5L)).thenReturn(Optional.of(config(50L, 3, true)));
        when(memoWorkflowStageRepository.findByConfigIdOrderByStageOrderAsc(50L))
                .thenReturn(List.of(
                        cfgStage(1, "Verification", "ROLE", 7L),
                        cfgStage(2, "Final Approval", "USER", 99L)));
        when(userRepository.findByRoleId(7L)).thenReturn(List.of(user(11L), user(12L)));
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L)));

        List<WorkflowStageInstance> saved = new ArrayList<>();
        when(stageInstanceRepository.save(any(WorkflowStageInstance.class))).thenAnswer(inv -> {
            WorkflowStageInstance s = inv.getArgument(0);
            saved.add(s);
            return s;
        });

        Memo submitted = engine.submitMemo(m, CREATOR);

        assertThat(submitted.getStatus()).isEqualTo(MemoStatus.PENDING_APPROVAL);
        assertThat(saved).hasSize(2);

        WorkflowStageInstance first = saved.get(0);
        assertThat(first.getStatus()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
        assertThat(first.getStageName()).isEqualTo("Verification");
        assertThat(first.getConfigId()).isEqualTo(50L);
        assertThat(first.getConfigVersion()).isEqualTo(3); // frozen revision
        assertThat(first.getAssigneeIds()).isEqualTo("11,12"); // every role member may act
        assertThat(first.getAssignee().getId()).isEqualTo(11L); // primary for display/notification
        assertThat(first.getRequireComment()).isTrue();
        assertThat(first.getEnteredAt()).isNotNull();

        WorkflowStageInstance second = saved.get(1);
        assertThat(second.getStatus()).isEqualTo(WorkflowStageStatus.DRAFT);
        assertThat(second.getStageName()).isEqualTo("Final Approval");
        assertThat(second.getConfigVersion()).isEqualTo(3);
        assertThat(second.getAssigneeIds()).isNull(); // pool frozen when the stage activates
    }

    @Test
    void submitMemo_configDriven_snapshotsPerStageGuardsWithConfigLevelFallback() {
        Memo m = memoWithType(5L);
        when(memoRepository.save(any(Memo.class))).thenAnswer(inv -> inv.getArgument(0));
        MemoWorkflowConfig cfg = config(50L, 1, true);
        cfg.setRequireDocumentVerification(true); // config-level fallback for stages without an explicit value
        when(memoWorkflowConfigRepository.findByMemoTypeId(5L)).thenReturn(Optional.of(cfg));

        // Stage 1 sets its guards explicitly; stage 2 leaves them null to exercise the fallback.
        MemoWorkflowStage explicit = cfgStage(1, "Docs", "USER", 11L);
        explicit.setRequireComment(false);
        explicit.setRequireDocument(true);
        MemoWorkflowStage inheriting = cfgStage(2, "Final", "USER", 12L);
        inheriting.setRequireComment(null);
        inheriting.setRequireDocument(null);

        when(memoWorkflowStageRepository.findByConfigIdOrderByStageOrderAsc(50L))
                .thenReturn(List.of(explicit, inheriting));
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L)));

        List<WorkflowStageInstance> saved = new ArrayList<>();
        when(stageInstanceRepository.save(any(WorkflowStageInstance.class))).thenAnswer(inv -> {
            WorkflowStageInstance s = inv.getArgument(0);
            saved.add(s);
            return s;
        });

        engine.submitMemo(m, CREATOR);

        assertThat(saved).hasSize(2);
        // Explicit per-stage guards are frozen as configured.
        assertThat(saved.get(0).getRequireComment()).isFalse();
        assertThat(saved.get(0).getRequireDocument()).isTrue();
        // Unset guards fall back: comment defaults on, document inherits the config-level flag.
        assertThat(saved.get(1).getRequireComment()).isTrue();
        assertThat(saved.get(1).getRequireDocument()).isTrue();
    }

    @Test
    void submitMemo_legacyCategoryPath_usesGlobalStagesAndMatrixAssignee() {
        Memo m = memo(CREATOR, MemoStatus.DRAFT);
        MemoCategory category = new MemoCategory();
        category.setId(9L);
        m.setCategory(category);

        when(memoRepository.save(any(Memo.class))).thenAnswer(inv -> inv.getArgument(0));
        WorkflowStage stage = new WorkflowStage();
        stage.setId(1L);
        stage.setName("Check");
        stage.setOrderIndex(0);
        when(workflowStageRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(stage));

        ApprovalRuleMatrix rule = new ApprovalRuleMatrix();
        rule.setUserId(42L);
        when(approvalRuleMatrixRepository.findByCategoryIdAndStageIdOrderByPriorityAsc(9L, 1L))
                .thenReturn(List.of(rule));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user(42L)));

        List<WorkflowStageInstance> saved = new ArrayList<>();
        when(stageInstanceRepository.save(any(WorkflowStageInstance.class))).thenAnswer(inv -> {
            WorkflowStageInstance s = inv.getArgument(0);
            saved.add(s);
            return s;
        });

        engine.submitMemo(m, CREATOR);

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getStage()).isSameAs(stage);
        assertThat(saved.get(0).getAssignee().getId()).isEqualTo(42L);
        assertThat(saved.get(0).getAssigneeIds()).isEqualTo("42");
        assertThat(saved.get(0).getConfigId()).isNull();
    }

    // ── decision: every eligible pool member may act ──────────────────────────

    @Test
    void approveStage_nonPrimaryPoolMemberCanActAndAdvances() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        WorkflowStageInstance second = instance(201L, MEMO_ID, WorkflowStageStatus.DRAFT, "99", 99L);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));
        when(stageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(MEMO_ID))
                .thenReturn(new ArrayList<>(List.of(first, second)));

        engine.approveStage(MEMO_ID, 200L, 12L, "Looks good");

        assertThat(first.getStatus()).isEqualTo(WorkflowStageStatus.APPROVED);
        assertThat(first.getActionBy()).isEqualTo(12L);
        assertThat(first.getComment()).isEqualTo("Looks good");
        assertThat(first.getCompletedAt()).isNotNull();
        // next stage advanced to in-progress and its frozen pool is untouched
        assertThat(second.getStatus()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
        assertThat(second.getAssigneeIds()).isEqualTo("99");
        verify(memoRepository, never()).findById(anyLong());
    }

    @Test
    void approveStage_nonEligibleUserDenied() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));

        assertThatThrownBy(() -> engine.approveStage(MEMO_ID, 200L, 13L, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an eligible approver");
        assertThat(first.getStatus()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
    }

    @Test
    void approveStage_requiresComment() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));

        assertThatThrownBy(() -> engine.approveStage(MEMO_ID, 200L, 12L, "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment is required");
    }

    @Test
    void approveStage_requiresDocumentWhenConfigured() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11", 11L);
        first.setRequireDocument(true);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));

        assertThatThrownBy(() -> engine.approveStage(MEMO_ID, 200L, 11L, "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("document must be attached");
    }

    @Test
    void rejectStage_requiresCommentButDoesNotRequireDocuments() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        first.setRequireDocument(true); // documents required for approval, not for rejection
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));
        when(memoRepository.save(any(Memo.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.rejectStage(MEMO_ID, 200L, 12L, "Missing receipts");

        assertThat(first.getStatus()).isEqualTo(WorkflowStageStatus.REJECTED);
        assertThat(first.getMemo().getStatus()).isEqualTo(MemoStatus.REJECTED);

        WorkflowStageInstance blankComment = instance(202L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        when(stageInstanceRepository.findByIdForUpdate(202L)).thenReturn(Optional.of(blankComment));
        assertThatThrownBy(() -> engine.rejectStage(MEMO_ID, 202L, 12L, ""))
                .isInstanceOf(IllegalStateException.class);
        assertThat(blankComment.getStatus()).isEqualTo(WorkflowStageStatus.IN_PROGRESS);
    }

    @Test
    void approveStage_lastStageMarksMemoApproved() {
        WorkflowStageInstance only = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11", 11L);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(only));
        when(stageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(MEMO_ID))
                .thenReturn(new ArrayList<>(List.of(only)));
        Memo stored = memo(CREATOR, MemoStatus.PENDING_APPROVAL);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(stored));
        when(memoRepository.save(any(Memo.class))).thenAnswer(inv -> inv.getArgument(0));

        engine.approveStage(MEMO_ID, 200L, 11L, "Final ok");

        assertThat(only.getStatus()).isEqualTo(WorkflowStageStatus.APPROVED);
        assertThat(stored.getStatus()).isEqualTo(MemoStatus.APPROVED);
        assertThat(stored.getApprovedDate()).isNotNull();
    }

    // ── delegation narrows the frozen pool ────────────────────────────────────

    @Test
    void delegateStage_narrowsPoolToDelegate() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));
        when(userRepository.findById(55L)).thenReturn(Optional.of(user(55L)));

        engine.delegateStage(MEMO_ID, 200L, 12L, 55L);

        assertThat(first.getAssignee().getId()).isEqualTo(55L);
        assertThat(first.getAssigneeIds()).isEqualTo("55");
        assertThat(first.getActionTaken()).isEqualTo("DELEGATED");
    }

    @Test
    void delegateStage_toSelfDenied() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12", 11L);
        when(stageInstanceRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(first));
        when(userRepository.findById(11L)).thenReturn(Optional.of(user(11L)));

        assertThatThrownBy(() -> engine.delegateStage(MEMO_ID, 200L, 11L, 11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("yourself");
    }

    // ── capability + pool introspection ───────────────────────────────────────

    @Test
    void hasActiveConfigForMemoType_trueOnlyWhenActiveAndStaged() {
        assertThat(engine.hasActiveConfigForMemoType(null)).isFalse();

        when(memoWorkflowConfigRepository.findByMemoTypeId(5L)).thenReturn(Optional.of(config(50L, 1, true)));
        when(memoWorkflowStageRepository.findByConfigIdOrderByStageOrderAsc(50L))
                .thenReturn(List.of(cfgStage(1, "Verify", "USER", 1L)));
        assertThat(engine.hasActiveConfigForMemoType(5L)).isTrue();

        when(memoWorkflowStageRepository.findByConfigIdOrderByStageOrderAsc(50L)).thenReturn(List.of());
        assertThat(engine.hasActiveConfigForMemoType(5L)).isFalse();

        when(memoWorkflowConfigRepository.findByMemoTypeId(6L)).thenReturn(Optional.of(config(60L, 1, false)));
        assertThat(engine.hasActiveConfigForMemoType(6L)).isFalse();
    }

    @Test
    void getEligibleAssigneesForStage_returnsFrozenPool() {
        WorkflowStageInstance first = instance(200L, MEMO_ID, WorkflowStageStatus.IN_PROGRESS, "11,12,13", 11L);
        when(stageInstanceRepository.findById(200L)).thenReturn(Optional.of(first));

        assertThat(engine.getEligibleAssigneesForStage(MEMO_ID, 200L))
                .containsExactly(11L, 12L, 13L);
    }
}