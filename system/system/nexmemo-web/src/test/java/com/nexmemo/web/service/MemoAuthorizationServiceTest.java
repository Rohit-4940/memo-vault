package com.nexmemo.web.service;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoWorkflowPermission;
import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.core.enums.WorkflowStageStatus;
import com.nexmemo.core.repository.MemoCcRepository;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.MemoWorkflowPermissionRepository;
import com.nexmemo.core.repository.WorkflowStageInstanceRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the memo resource-based authorization core (Phase 3 hardening).
 * These assert the decisions that previously allowed BOLA/IDOR access to any memo by id.
 */
@ExtendWith(MockitoExtension.class)
class MemoAuthorizationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private MemoCcRepository memoCcRepository;
    @Mock private WorkflowStageInstanceRepository workflowStageInstanceRepository;
    @Mock private MemoWorkflowPermissionRepository memoWorkflowPermissionRepository;
    @Mock private AuditEventPublisher auditEventPublisher;

    @InjectMocks private MemoAuthorizationService service;

    private static final Long USER = 10L;
    private static final Long ADMIN = 1L;
    private static final Long MEMO_ID = 7L;

    private User userWithRole(Long id, Long roleId, RoleType type, String roleName) {
        Role role = new Role();
        role.setId(roleId);
        role.setRoleType(type);
        role.setRoleName(roleName);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private Memo memoOwnedBy(Long creatorId) {
        Memo memo = new Memo();
        memo.setId(MEMO_ID);
        memo.setCreatedBy(creatorId);
        memo.setStatus(MemoStatus.PENDING_APPROVAL);
        memo.setApprovalLevel(1);
        return memo;
    }

    // ── isAdmin ───────────────────────────────────────────────────────────────

    @Test
    void isAdmin_trueForAdminAndSuperAdmin() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(userWithRole(ADMIN, 1L, RoleType.ADMIN, "ADMIN")));
        assertThat(service.isAdmin(ADMIN)).isTrue();

        when(userRepository.findByIdWithRole(2L))
                .thenReturn(Optional.of(userWithRole(2L, 2L, RoleType.SUPER_ADMIN, "SUPER_ADMIN")));
        assertThat(service.isAdmin(2L)).isTrue();
    }

    @Test
    void isAdmin_falseForRegularUserAndNull() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, 3L, RoleType.USER, "USER")));
        assertThat(service.isAdmin(USER)).isFalse();
        assertThat(service.isAdmin(null)).isFalse();
    }

    // ── canViewMemo ───────────────────────────────────────────────────────────

    @Test
    void canViewMemo_creatorAllowed() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(USER)));
        assertThat(service.canViewMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canViewMemo_currentApproverAllowed() {
        User approver = new User();
        approver.setId(USER);
        Memo memo = memoOwnedBy(999L);
        memo.setCurrentApprover(approver);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canViewMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canViewMemo_ccRecipientAllowed() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, USER))
                .thenReturn(Optional.of(new com.nexmemo.core.entity.MemoCc()));

        assertThat(service.canViewMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canViewMemo_stageAssigneeAllowed() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, USER)).thenReturn(Optional.empty());
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(MEMO_ID, USER)).thenReturn(true);

        assertThat(service.canViewMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canViewMemo_unrelatedUserDenied() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, USER)).thenReturn(Optional.empty());
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(MEMO_ID, USER)).thenReturn(false);

        assertThat(service.canViewMemo(USER, MEMO_ID)).isFalse();
    }

    @Test
    void canViewMemo_deletedMemoDeniedEvenForAdmin() {
        Memo memo = memoOwnedBy(USER);
        memo.setIsDeleted(true);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canViewMemo(USER, MEMO_ID)).isFalse();
        assertThat(service.canViewMemo(ADMIN, MEMO_ID)).isFalse();
    }

    @Test
    void canViewMemo_missingMemoDenied() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.empty());
        assertThat(service.canViewMemo(USER, MEMO_ID)).isFalse();
        assertThat(service.canViewMemo(USER, null)).isFalse();
    }

    // ── canEditMemo ───────────────────────────────────────────────────────────

    @Test
    void canEditMemo_creatorDraftAllowed() {
        Memo memo = memoOwnedBy(USER);
        memo.setStatus(MemoStatus.DRAFT);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canEditMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canEditMemo_creatorRevisionAllowed() {
        Memo memo = memoOwnedBy(USER);
        memo.setStatus(MemoStatus.REVISION_REQUESTED);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canEditMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canEditMemo_creatorCannotEditSubmittedMemo() {
        Memo memo = memoOwnedBy(USER);
        memo.setStatus(MemoStatus.PENDING_APPROVAL);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canEditMemo(USER, MEMO_ID)).isFalse();
    }

    @Test
    void canEditMemo_nonCreatorDenied() {
        Memo memo = memoOwnedBy(999L);
        memo.setStatus(MemoStatus.DRAFT);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canEditMemo(USER, MEMO_ID)).isFalse();
    }

    @Test
    void canEditMemo_adminAllowed() {
        Memo memo = memoOwnedBy(999L);
        memo.setStatus(MemoStatus.PENDING_APPROVAL);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(userWithRole(ADMIN, 1L, RoleType.ADMIN, "ADMIN")));

        assertThat(service.canEditMemo(ADMIN, MEMO_ID)).isTrue();
    }

    // ── canActOnMemo ──────────────────────────────────────────────────────────

    @Test
    void canActOnMemo_currentApproverAllowed() {
        User approver = new User();
        approver.setId(USER);
        Memo memo = memoOwnedBy(999L);
        memo.setCurrentApprover(approver);
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memo));

        assertThat(service.canActOnMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canActOnMemo_inProgressStageAssigneeAllowed() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, USER, WorkflowStageStatus.IN_PROGRESS)).thenReturn(true);

        assertThat(service.canActOnMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canActOnMemo_adminHasNoBlanketBypass() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, ADMIN, WorkflowStageStatus.IN_PROGRESS)).thenReturn(false);

        assertThat(service.canActOnMemo(ADMIN, MEMO_ID)).isFalse();
        verify(userRepository, never()).findByIdWithRole(any());
    }

    @Test
    void canActOnMemo_unrelatedUserDenied() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, USER, WorkflowStageStatus.IN_PROGRESS)).thenReturn(false);

        assertThat(service.canActOnMemo(USER, MEMO_ID)).isFalse();
    }

    // ── canAcknowledgeMemo ────────────────────────────────────────────────────

    @Test
    void canAcknowledgeMemo_ccRecipientAllowed_nonRecipientDenied() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, USER))
                .thenReturn(Optional.of(new com.nexmemo.core.entity.MemoCc()));
        assertThat(service.canAcknowledgeMemo(USER, MEMO_ID)).isTrue();

        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, 55L)).thenReturn(Optional.empty());
        assertThat(service.canAcknowledgeMemo(55L, MEMO_ID)).isFalse();
    }

    // ── canAccessBranch ───────────────────────────────────────────────────────

    @Test
    void canAccessBranch_adminBypassesMembership_memberAllowed_nonMemberDenied() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(userWithRole(ADMIN, 1L, RoleType.ADMIN, "ADMIN")));
        assertThat(service.canAccessBranch(ADMIN, 5L)).isTrue();

        when(userRepository.existsByIdAndBranches_Id(USER, 5L)).thenReturn(true);
        when(userRepository.existsByIdAndBranches_Id(USER, 6L)).thenReturn(false);
        assertThat(service.canAccessBranch(USER, 5L)).isTrue();
        assertThat(service.canAccessBranch(USER, 6L)).isFalse();
    }

    // ── canCreateMemoOfType (MemoWorkflowPermission) ──────────────────────────

    @Test
    void canCreateMemoOfType_nullTypeAllowed() {
        assertThat(service.canCreateMemoOfType(USER, null)).isTrue();
        verifyNoInteractions(memoWorkflowPermissionRepository);
    }

    @Test
    void canCreateMemoOfType_adminAllowed() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(userWithRole(ADMIN, 1L, RoleType.ADMIN, "ADMIN")));
        assertThat(service.canCreateMemoOfType(ADMIN, 4L)).isTrue();
    }

    @Test
    void canCreateMemoOfType_emptyMatrixAllowedForBackwardCompatibility() {
        when(memoWorkflowPermissionRepository.findByMemoTypeId(4L)).thenReturn(List.of());
        assertThat(service.canCreateMemoOfType(USER, 4L)).isTrue();
    }

    @Test
    void canCreateMemoOfType_matchingRoleWithCreateAllowed() {
        MemoWorkflowPermission perm = new MemoWorkflowPermission();
        perm.setRoleId(3L);
        perm.setCanCreate(true);
        when(memoWorkflowPermissionRepository.findByMemoTypeId(4L)).thenReturn(List.of(perm));
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, 3L, RoleType.MAKER, "MAKER")));

        assertThat(service.canCreateMemoOfType(USER, 4L)).isTrue();
    }

    @Test
    void canCreateMemoOfType_matchingRoleWithoutCreateDenied() {
        MemoWorkflowPermission perm = new MemoWorkflowPermission();
        perm.setRoleId(3L);
        perm.setCanCreate(false);
        when(memoWorkflowPermissionRepository.findByMemoTypeId(4L)).thenReturn(List.of(perm));
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, 3L, RoleType.MAKER, "MAKER")));

        assertThat(service.canCreateMemoOfType(USER, 4L)).isFalse();
    }

    @Test
    void canCreateMemoOfType_nonMatchingRoleDenied() {
        MemoWorkflowPermission perm = new MemoWorkflowPermission();
        perm.setRoleId(3L);
        perm.setCanCreate(true);
        when(memoWorkflowPermissionRepository.findByMemoTypeId(4L)).thenReturn(List.of(perm));
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, 9L, RoleType.USER, "USER")));

        assertThat(service.canCreateMemoOfType(USER, 4L)).isFalse();
    }

    @Test
    void canCreateMemoOfType_roleNameMatchSupported() {
        MemoWorkflowPermission perm = new MemoWorkflowPermission();
        perm.setRoleName("MAKER");
        perm.setCanCreate(true);
        when(memoWorkflowPermissionRepository.findByMemoTypeId(4L)).thenReturn(List.of(perm));
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, 9L, RoleType.MAKER, "MAKER")));

        assertThat(service.canCreateMemoOfType(USER, 4L)).isTrue();
    }

    // ── assertions audit + throw ──────────────────────────────────────────────

    @Test
    void assertCanViewMemo_deniedAuditsAndThrows() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, USER)).thenReturn(Optional.empty());
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(MEMO_ID, USER)).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanViewMemo(USER, MEMO_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditEventPublisher).accessDenied(eq(USER), eq("MEMO"), eq(MEMO_ID), any());
    }

    @Test
    void assertCanActOnMemo_deniedAuditsAndThrows() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, USER, WorkflowStageStatus.IN_PROGRESS)).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanActOnMemo(USER, MEMO_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditEventPublisher).accessDenied(eq(USER), eq("MEMO"), eq(MEMO_ID), any());
    }

    @Test
    void assertCanViewMemo_allowedDoesNotAudit() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(USER)));
        service.assertCanViewMemo(USER, MEMO_ID);
        verifyNoInteractions(auditEventPublisher);
    }

    // ── assignee pool (Phase 5: every eligible middle approver) ───────────────

    private WorkflowStageInstance stageInstance(WorkflowStageStatus status, String assigneeIds) {
        WorkflowStageInstance instance = new WorkflowStageInstance();
        instance.setStatus(status);
        instance.setAssigneeIds(assigneeIds);
        return instance;
    }

    @Test
    void canViewMemo_poolMemberAllowed() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(memoCcRepository.findByMemoIdAndUserId(MEMO_ID, USER)).thenReturn(Optional.empty());
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(MEMO_ID, USER)).thenReturn(false);
        when(workflowStageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(MEMO_ID))
                .thenReturn(List.of(stageInstance(WorkflowStageStatus.IN_PROGRESS, "99," + USER)));

        assertThat(service.canViewMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canActOnMemo_poolMemberOfInProgressStageAllowed() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, USER, WorkflowStageStatus.IN_PROGRESS)).thenReturn(false);
        when(workflowStageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(MEMO_ID))
                .thenReturn(List.of(stageInstance(WorkflowStageStatus.IN_PROGRESS, "99," + USER)));

        assertThat(service.canActOnMemo(USER, MEMO_ID)).isTrue();
    }

    @Test
    void canActOnMemo_poolMemberOfNonInProgressStageDenied() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, USER, WorkflowStageStatus.IN_PROGRESS)).thenReturn(false);
        when(workflowStageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(MEMO_ID))
                .thenReturn(List.of(stageInstance(WorkflowStageStatus.DRAFT, String.valueOf(USER))));

        assertThat(service.canActOnMemo(USER, MEMO_ID)).isFalse();
    }

    @Test
    void canActOnMemo_poolMemberNotInListDenied() {
        when(memoRepository.findById(MEMO_ID)).thenReturn(Optional.of(memoOwnedBy(999L)));
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                MEMO_ID, USER, WorkflowStageStatus.IN_PROGRESS)).thenReturn(false);
        when(workflowStageInstanceRepository.findByMemoIdOrderByEnteredAtAsc(MEMO_ID))
                .thenReturn(List.of(stageInstance(WorkflowStageStatus.IN_PROGRESS, "88,99")));

        assertThat(service.canActOnMemo(USER, MEMO_ID)).isFalse();
    }
}
