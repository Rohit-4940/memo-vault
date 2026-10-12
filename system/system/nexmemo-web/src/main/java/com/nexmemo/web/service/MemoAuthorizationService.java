package com.nexmemo.web.service;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoWorkflowPermission;
import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.core.enums.WorkflowStageStatus;
import com.nexmemo.core.repository.MemoCcRepository;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.MemoWorkflowPermissionRepository;
import com.nexmemo.core.repository.WorkflowStageInstanceRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Central, server-side authorization for the memo module.
 *
 * <p>SECURITY RULE: the client is never trusted to declare which memo it may read, edit or act
 * on. Every memo read/write routes through one of the {@code can*}/{@code assert*} checks here,
 * which resolve the caller from the JWT principal and the resource from the database. This fixes
 * the previous BOLA/IDOR behaviour where endpoints accepted an arbitrary {@code {id}} with no
 * ownership or assignment check.</p>
 *
 * <p>Privilege model:</p>
 * <ul>
 *   <li><b>View</b>: admin, creator, current approver, CC recipient, or a user assigned to any
 *       workflow stage of the memo. Soft-deleted memos are treated as non-existent.</li>
 *   <li><b>Edit</b>: admin, or the creator while the memo is still DRAFT / REVISION_REQUESTED.</li>
 *   <li><b>Act</b> (approve / reject / return / forward / backward / stage actions): ONLY the
 *       current approver or the assignee of the in-progress stage. Administrative privileges do
 *       <b>not</b> grant approval authority over every transaction — configuration authority is
 *       deliberately separated from financial approval authority.</li>
 *   <li><b>Create of a memo type</b>: governed by the per-memo-type {@code MemoWorkflowPermission}
 *       matrix when it is configured; when no matrix exists the operation is allowed for backward
 *       compatibility.</li>
 * </ul>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoAuthorizationService {

    private static final String ENTITY_MEMO = "MEMO";
    private static final String ENTITY_MEMO_TYPE = "MEMO_TYPE";
    private static final String ENTITY_BRANCH = "BRANCH";

    private final UserRepository userRepository;
    private final MemoRepository memoRepository;
    private final MemoCcRepository memoCcRepository;
    private final WorkflowStageInstanceRepository workflowStageInstanceRepository;
    private final MemoWorkflowPermissionRepository memoWorkflowPermissionRepository;
    private final AuditEventPublisher auditEventPublisher;

    // ── Role checks ───────────────────────────────────────────────────────────

    /** Admins (ADMIN / SUPER_ADMIN) bypass resource-membership checks for reads. */
    @Transactional(readOnly = true)
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findByIdWithRole(userId)
                .map(User::getRole)
                .map(Role::getRoleType)
                .map(type -> type == RoleType.ADMIN || type == RoleType.SUPER_ADMIN)
                .orElse(false);
    }

    // ── Resource checks ───────────────────────────────────────────────────────

    /** May the caller read this memo (and its history, routing, documents, CC list, stages)? */
    @Transactional(readOnly = true)
    public boolean canViewMemo(Long userId, Long memoId) {
        if (userId == null || memoId == null) {
            return false;
        }
        Memo memo = memoRepository.findById(memoId).orElse(null);
        if (memo == null || Boolean.TRUE.equals(memo.getIsDeleted())) {
            return false;
        }
        if (isAdmin(userId)) {
            return true;
        }
        if (userId.equals(memo.getCreatedBy())) {
            return true;
        }
        if (memo.getCurrentApprover() != null && userId.equals(memo.getCurrentApprover().getId())) {
            return true;
        }
        if (memoCcRepository.findByMemoIdAndUserId(memoId, userId).isPresent()) {
            return true;
        }
        return workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(memoId, userId);
    }

    /** May the caller modify this memo's content (submit, cancel, edit drafts)? */
    @Transactional(readOnly = true)
    public boolean canEditMemo(Long userId, Long memoId) {
        if (userId == null || memoId == null) {
            return false;
        }
        Memo memo = memoRepository.findById(memoId).orElse(null);
        if (memo == null || Boolean.TRUE.equals(memo.getIsDeleted())) {
            return false;
        }
        if (isAdmin(userId)) {
            return true;
        }
        boolean editable = memo.getStatus() == MemoStatus.DRAFT
                || memo.getStatus() == MemoStatus.REVISION_REQUESTED;
        return editable && userId.equals(memo.getCreatedBy());
    }

    /**
     * May the caller perform a workflow decision on this memo (approve/reject/return/forward/
     * backward) or act on one of its stages? Restricted to the current approver or the assignee
     * of the in-progress stage; admins do NOT get a blanket bypass.
     */
    @Transactional(readOnly = true)
    public boolean canActOnMemo(Long userId, Long memoId) {
        if (userId == null || memoId == null) {
            return false;
        }
        Memo memo = memoRepository.findById(memoId).orElse(null);
        if (memo == null || Boolean.TRUE.equals(memo.getIsDeleted())) {
            return false;
        }
        if (memo.getCurrentApprover() != null && userId.equals(memo.getCurrentApprover().getId())) {
            return true;
        }
        return workflowStageInstanceRepository.existsByMemoIdAndAssigneeIdAndStatus(
                memoId, userId, WorkflowStageStatus.IN_PROGRESS);
    }

    /** May the caller acknowledge this memo? Only CC recipients (and admins) may. */
    @Transactional(readOnly = true)
    public boolean canAcknowledgeMemo(Long userId, Long memoId) {
        if (userId == null || memoId == null) {
            return false;
        }
        Memo memo = memoRepository.findById(memoId).orElse(null);
        if (memo == null || Boolean.TRUE.equals(memo.getIsDeleted())) {
            return false;
        }
        if (isAdmin(userId)) {
            return true;
        }
        return memoCcRepository.findByMemoIdAndUserId(memoId, userId).isPresent();
    }

    /** Branch data isolation: members of the branch and admins only. */
    @Transactional(readOnly = true)
    public boolean canAccessBranch(Long userId, Long branchId) {
        if (userId == null || branchId == null) {
            return false;
        }
        if (isAdmin(userId)) {
            return true;
        }
        return userRepository.existsByIdAndBranches_Id(userId, branchId);
    }

    /**
     * May the caller create a memo of the given type? Enforcement is driven by the per-memo-type
     * permission matrix. When the matrix is empty the operation is allowed (the feature has not
     * been configured yet), which keeps existing behaviour intact until an administrator opts in.
     */
    @Transactional(readOnly = true)
    public boolean canCreateMemoOfType(Long userId, Long memoTypeId) {
        if (userId == null) {
            return false;
        }
        if (memoTypeId == null) {
            return true;
        }
        if (isAdmin(userId)) {
            return true;
        }
        List<MemoWorkflowPermission> permissions = memoWorkflowPermissionRepository.findByMemoTypeId(memoTypeId);
        if (permissions.isEmpty()) {
            return true;
        }
        User user = userRepository.findByIdWithRole(userId).orElse(null);
        if (user == null || user.getRole() == null) {
            return false;
        }
        Long roleId = user.getRole().getId();
        String roleName = user.getRole().getRoleName();
        return permissions.stream().anyMatch(p ->
                Boolean.TRUE.equals(p.getCanCreate()) && matchesRole(p, roleId, roleName));
    }

    private boolean matchesRole(MemoWorkflowPermission permission, Long roleId, String roleName) {
        if (permission.getRoleId() != null) {
            return Objects.equals(permission.getRoleId(), roleId);
        }
        return permission.getRoleName() != null && roleName != null
                && permission.getRoleName().equalsIgnoreCase(roleName);
    }

    // ── Assertions (throw + audit) ────────────────────────────────────────────

    public void assertCanViewMemo(Long userId, Long memoId) {
        if (!canViewMemo(userId, memoId)) {
            deny(userId, ENTITY_MEMO, memoId, "memo view denied");
        }
    }

    public void assertCanEditMemo(Long userId, Long memoId) {
        if (!canEditMemo(userId, memoId)) {
            deny(userId, ENTITY_MEMO, memoId, "memo edit denied");
        }
    }

    public void assertCanActOnMemo(Long userId, Long memoId) {
        if (!canActOnMemo(userId, memoId)) {
            deny(userId, ENTITY_MEMO, memoId, "memo decision denied (not current approver or stage assignee)");
        }
    }

    public void assertCanAcknowledgeMemo(Long userId, Long memoId) {
        if (!canAcknowledgeMemo(userId, memoId)) {
            deny(userId, ENTITY_MEMO, memoId, "memo acknowledge denied (not a CC recipient)");
        }
    }

    public void assertCanAccessBranch(Long userId, Long branchId) {
        if (!canAccessBranch(userId, branchId)) {
            deny(userId, ENTITY_BRANCH, branchId, "branch memo access denied");
        }
    }

    public void assertCanCreateMemoOfType(Long userId, Long memoTypeId) {
        if (!canCreateMemoOfType(userId, memoTypeId)) {
            deny(userId, ENTITY_MEMO_TYPE, memoTypeId, "memo type creation denied");
        }
    }

    private void deny(Long userId, String entityType, Long entityId, String reason) {
        log.warn("Memo authorization denied: userId={} entity={}#{} reason={}", userId, entityType, entityId, reason);
        auditEventPublisher.accessDenied(userId, entityType, entityId, reason);
        throw new AccessDeniedException("You are not authorized to perform this action on the requested memo");
    }
}
