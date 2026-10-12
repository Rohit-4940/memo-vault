package com.nexmemo.web.service;

import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.ChatConversationType;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.core.repository.MemoCcRepository;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.WorkflowStageInstanceRepository;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central, server-side authorization for the chat module.
 *
 * SECURITY RULE: the client is never trusted to declare who it is or which resource it may
 * touch. Every chat read/write must pass through one of the {@code can*} checks here, which
 * resolve the caller from the JWT principal and the resource from the database.
 *
 * This replaces the previous BOLA/IDOR-prone behaviour where endpoints accepted arbitrary
 * memoId / branchId / receiverId values from the request.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private static final String ENTITY_BRANCH = "BRANCH";
    private static final String ENTITY_MEMO = "MEMO";
    private static final String ENTITY_USER = "USER";
    private static final String ENTITY_MESSAGE = "CHAT_MESSAGE";
    private static final String ENTITY_CONVERSATION = "CHAT_CONVERSATION";
    private static final String ENTITY_DEPARTMENT = "DEPARTMENT";

    private final UserRepository userRepository;
    private final MemoRepository memoRepository;
    private final MemoCcRepository memoCcRepository;
    private final WorkflowStageInstanceRepository workflowStageInstanceRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatAuditService chatAuditService;

    // ── Role checks ───────────────────────────────────────────────────────────

    /** Admins (ADMIN / SUPER_ADMIN) bypass resource membership checks. */
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

    /**
     * Branch chat is visible to members of that branch and to admins.
     * A user belongs to a branch via the {@code user_branches} join table.
     */
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
     * Memo discussion is visible to: admins, the memo creator, the current approver,
     * any CC recipient, and any user assigned to a workflow stage of the memo.
     * Deleted memos are treated as non-existent.
     */
    @Transactional(readOnly = true)
    public boolean canAccessMemo(Long userId, Long memoId) {
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

    /**
     * 1:1 conversation: the peer must exist and must not be the caller.
     * (Any active employee may DM any other active employee — directory-wide messaging
     * is an accepted bank-internal policy; tighten here if the policy changes.)
     */
    @Transactional(readOnly = true)
    public boolean canDirectMessage(Long userId, Long otherId) {
        if (userId == null || otherId == null || userId.equals(otherId)) {
            return false;
        }
        return userRepository.existsById(otherId);
    }

    /**
     * Department channel is visible to members of that department and to admins.
     */
    @Transactional(readOnly = true)
    public boolean canAccessDepartment(Long userId, Long departmentId) {
        if (userId == null || departmentId == null) {
            return false;
        }
        if (isAdmin(userId)) {
            return true;
        }
        return userRepository.existsByIdAndDepartments_Id(userId, departmentId);
    }

    /**
     * Authoritative conversation access check, dispatched by conversation type.
     * DIRECT/GROUP are membership-only (admins are NOT given silent access to private
     * conversations); BRANCH/DEPARTMENT/MEMO delegate to the underlying resource rule.
     */
    @Transactional(readOnly = true)
    public boolean canAccessConversation(Long userId, ChatConversation conversation) {
        if (userId == null || conversation == null) {
            return false;
        }
        ChatConversationType type = conversation.getConversationType();
        if (type == null) {
            return false;
        }
        return switch (type) {
            case DIRECT, GROUP -> chatParticipantRepository
                    .existsByConversationIdAndUserIdAndIsActiveTrue(conversation.getId(), userId);
            case BRANCH -> canAccessBranch(userId, conversation.getBranchId());
            case DEPARTMENT -> canAccessDepartment(userId, conversation.getDepartmentId());
            case MEMO -> canAccessMemo(userId, conversation.getMemoId());
        };
    }

    @Transactional(readOnly = true)
    public boolean canAccessConversation(Long userId, Long conversationId) {
        if (conversationId == null) {
            return false;
        }
        return chatConversationRepository.findById(conversationId)
                .map(conversation -> canAccessConversation(userId, conversation))
                .orElse(false);
    }

    // ── Assertions (throw + audit) ────────────────────────────────────────────

    public void assertCanAccessBranch(Long userId, Long branchId) {
        if (!canAccessBranch(userId, branchId)) {
            deny(userId, ENTITY_BRANCH, branchId, "branch chat access denied");
        }
    }

    public void assertCanAccessMemo(Long userId, Long memoId) {
        if (!canAccessMemo(userId, memoId)) {
            deny(userId, ENTITY_MEMO, memoId, "memo discussion access denied");
        }
    }

    public void assertCanAccessDepartment(Long userId, Long departmentId) {
        if (!canAccessDepartment(userId, departmentId)) {
            deny(userId, ENTITY_DEPARTMENT, departmentId, "department chat access denied");
        }
    }

    public void assertCanDirectMessage(Long userId, Long otherId) {
        if (!canDirectMessage(userId, otherId)) {
            deny(userId, ENTITY_USER, otherId, "direct message target not permitted");
        }
    }

    public void assertCanAccessConversation(Long userId, ChatConversation conversation) {
        if (!canAccessConversation(userId, conversation)) {
            deny(userId, ENTITY_CONVERSATION, conversation != null ? conversation.getId() : null,
                    "conversation access denied");
        }
    }

    /** Only ADMIN/SUPER_ADMIN may create group conversations (bank governance policy). */
    public void assertCanCreateGroup(Long userId) {
        if (!isAdmin(userId)) {
            deny(userId, ENTITY_CONVERSATION, null, "group creation requires admin");
        }
    }

    /**
     * May {@code userId} administer this conversation (rename/archive/add/remove members)?
     * Admins always may; for GROUP/DIRECT the caller must be OWNER or MODERATOR.
     */
    @Transactional(readOnly = true)
    public void assertCanManageConversation(Long userId, ChatConversation conversation) {
        if (conversation == null) {
            throw new IllegalArgumentException("Conversation not found");
        }
        if (isAdmin(userId)) {
            return;
        }
        if (conversation.getConversationType() == ChatConversationType.DIRECT
                || conversation.getConversationType() == ChatConversationType.GROUP) {
            boolean privileged = chatParticipantRepository.findByConversationIdAndUserId(conversation.getId(), userId)
                    .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                    .map(p -> p.getParticipantRole() == com.nexmemo.core.enums.ChatParticipantRole.OWNER
                            || p.getParticipantRole() == com.nexmemo.core.enums.ChatParticipantRole.MODERATOR)
                    .orElse(false);
            if (privileged) {
                return;
            }
        }
        deny(userId, ENTITY_CONVERSATION, conversation.getId(), "conversation management denied");
    }

    /**
     * May {@code userId} mark this message as read? Yes if the caller is the intended
     * recipient, an authorized member of the owning branch, or an admin.
     */
    @Transactional(readOnly = true)
    public void assertCanAcknowledgeMessage(Long userId, ChatMessage msg) {
        if (msg == null) {
            throw new IllegalArgumentException("Message not found");
        }
        if (userId != null && userId.equals(msg.getReceiverId())) {
            return;
        }
        if (msg.getBranchId() != null && canAccessBranch(userId, msg.getBranchId())) {
            return;
        }
        if (isAdmin(userId)) {
            return;
        }
        deny(userId, ENTITY_MESSAGE, msg.getId(), "message acknowledge denied");
    }

    private void deny(Long userId, String entityType, Long entityId, String reason) {
        log.warn("Chat authorization denied: userId={} entity={}#{} reason={}", userId, entityType, entityId, reason);
        chatAuditService.accessDenied(userId, entityType, entityId, reason);
        throw new AccessDeniedException("You are not authorized to access this conversation");
    }
}
