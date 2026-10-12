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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the chat resource-based authorization core (Phase 0 hardening).
 * These assert the exact decisions that previously allowed BOLA/IDOR access.
 */
@ExtendWith(MockitoExtension.class)
class ChatAuthorizationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private MemoCcRepository memoCcRepository;
    @Mock private WorkflowStageInstanceRepository workflowStageInstanceRepository;
    @Mock private ChatConversationRepository chatConversationRepository;
    @Mock private ChatParticipantRepository chatParticipantRepository;
    @Mock private ChatAuditService chatAuditService;

    @InjectMocks private ChatAuthorizationService service;

    private static final Long USER = 10L;
    private static final Long ADMIN = 1L;

    private void stubRole(Long userId, RoleType type) {
        Role role = new Role();
        role.setRoleType(type);
        User user = new User();
        user.setId(userId);
        user.setRole(role);
        when(userRepository.findByIdWithRole(userId)).thenReturn(Optional.of(user));
    }

    // ── isAdmin ───────────────────────────────────────────────────────────────

    @Test
    void isAdmin_trueForAdminAndSuperAdmin() {
        stubRole(ADMIN, RoleType.ADMIN);
        assertThat(service.isAdmin(ADMIN)).isTrue();

        Role superRole = new Role();
        superRole.setRoleType(RoleType.SUPER_ADMIN);
        User superUser = new User();
        superUser.setId(2L);
        superUser.setRole(superRole);
        when(userRepository.findByIdWithRole(2L)).thenReturn(Optional.of(superUser));
        assertThat(service.isAdmin(2L)).isTrue();
    }

    @Test
    void isAdmin_falseForRegularUserAndNull() {
        stubRole(USER, RoleType.USER);
        assertThat(service.isAdmin(USER)).isFalse();
        assertThat(service.isAdmin(null)).isFalse();
    }

    // ── branch ────────────────────────────────────────────────────────────────

    @Test
    void canAccessBranch_adminBypassesMembership() {
        stubRole(ADMIN, RoleType.ADMIN);
        assertThat(service.canAccessBranch(ADMIN, 99L)).isTrue();
        verify(userRepository, never()).existsByIdAndBranches_Id(any(), any());
    }

    @Test
    void canAccessBranch_memberAllowed_nonMemberDenied() {
        stubRole(USER, RoleType.USER);
        when(userRepository.existsByIdAndBranches_Id(USER, 5L)).thenReturn(true);
        when(userRepository.existsByIdAndBranches_Id(USER, 6L)).thenReturn(false);

        assertThat(service.canAccessBranch(USER, 5L)).isTrue();
        assertThat(service.canAccessBranch(USER, 6L)).isFalse();
    }

    @Test
    void canAccessBranch_nullArgsDenied() {
        assertThat(service.canAccessBranch(null, 5L)).isFalse();
        assertThat(service.canAccessBranch(USER, null)).isFalse();
    }

    // ── memo ──────────────────────────────────────────────────────────────────

    @Test
    void canAccessMemo_creatorAllowed() {
        stubRole(USER, RoleType.USER);
        Memo memo = new Memo();
        memo.setCreatedBy(USER);
        when(memoRepository.findById(7L)).thenReturn(Optional.of(memo));

        assertThat(service.canAccessMemo(USER, 7L)).isTrue();
    }

    @Test
    void canAccessMemo_currentApproverAllowed() {
        stubRole(USER, RoleType.USER);
        User approver = new User();
        approver.setId(USER);
        Memo memo = new Memo();
        memo.setCreatedBy(999L);
        memo.setCurrentApprover(approver);
        when(memoRepository.findById(7L)).thenReturn(Optional.of(memo));

        assertThat(service.canAccessMemo(USER, 7L)).isTrue();
    }

    @Test
    void canAccessMemo_ccRecipientAllowed() {
        stubRole(USER, RoleType.USER);
        Memo memo = new Memo();
        memo.setCreatedBy(999L);
        when(memoRepository.findById(7L)).thenReturn(Optional.of(memo));
        when(memoCcRepository.findByMemoIdAndUserId(7L, USER)).thenReturn(Optional.of(new com.nexmemo.core.entity.MemoCc()));

        assertThat(service.canAccessMemo(USER, 7L)).isTrue();
    }

    @Test
    void canAccessMemo_stageAssigneeAllowed() {
        stubRole(USER, RoleType.USER);
        Memo memo = new Memo();
        memo.setCreatedBy(999L);
        when(memoRepository.findById(7L)).thenReturn(Optional.of(memo));
        when(memoCcRepository.findByMemoIdAndUserId(7L, USER)).thenReturn(Optional.empty());
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(7L, USER)).thenReturn(true);

        assertThat(service.canAccessMemo(USER, 7L)).isTrue();
    }

    @Test
    void canAccessMemo_unrelatedUserDenied() {
        stubRole(USER, RoleType.USER);
        Memo memo = new Memo();
        memo.setCreatedBy(999L);
        when(memoRepository.findById(7L)).thenReturn(Optional.of(memo));
        when(memoCcRepository.findByMemoIdAndUserId(7L, USER)).thenReturn(Optional.empty());
        when(workflowStageInstanceRepository.existsByMemoIdAndAssigneeId(7L, USER)).thenReturn(false);

        assertThat(service.canAccessMemo(USER, 7L)).isFalse();
    }

    @Test
    void canAccessMemo_deletedMemoDeniedEvenForAdmin() {
        Memo memo = new Memo();
        memo.setCreatedBy(USER);
        memo.setIsDeleted(true);
        when(memoRepository.findById(7L)).thenReturn(Optional.of(memo));

        assertThat(service.canAccessMemo(USER, 7L)).isFalse();
        assertThat(service.canAccessMemo(ADMIN, 7L)).isFalse();
    }

    // ── direct message ────────────────────────────────────────────────────────

    @Test
    void canDirectMessage_existingPeerAllowed_selfIncluded() {
        when(userRepository.existsById(20L)).thenReturn(true);
        assertThat(service.canDirectMessage(USER, 20L)).isTrue();
        assertThat(service.canDirectMessage(USER, USER)).isFalse();
        assertThat(service.canDirectMessage(USER, null)).isFalse();
    }

    @Test
    void canDirectMessage_missingPeerDenied() {
        when(userRepository.existsById(20L)).thenReturn(false);
        assertThat(service.canDirectMessage(USER, 20L)).isFalse();
    }

    // ── assertions audit + throw ──────────────────────────────────────────────

    @Test
    void assertCanAccessBranch_deniedAuditsAndThrows() {
        stubRole(USER, RoleType.USER);
        when(userRepository.existsByIdAndBranches_Id(USER, 6L)).thenReturn(false);

        assertThatThrownBy(() -> service.assertCanAccessBranch(USER, 6L))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatAuditService).accessDenied(eq(USER), eq("BRANCH"), eq(6L), any());
    }

    @Test
    void assertCanAccessBranch_allowedDoesNotAudit() {
        stubRole(USER, RoleType.USER);
        when(userRepository.existsByIdAndBranches_Id(USER, 5L)).thenReturn(true);

        service.assertCanAccessBranch(USER, 5L);
        verifyNoInteractions(chatAuditService);
    }

    // ── message acknowledgement ───────────────────────────────────────────────

    @Test
    void assertCanAcknowledgeMessage_recipientAllowed() {
        ChatMessage msg = ChatMessage.builder().receiverId(USER).branchId(5L).build();
        service.assertCanAcknowledgeMessage(USER, msg);
        verifyNoInteractions(chatAuditService);
    }

    @Test
    void assertCanAcknowledgeMessage_branchMemberAllowed() {
        stubRole(USER, RoleType.USER);
        when(userRepository.existsByIdAndBranches_Id(USER, 5L)).thenReturn(true);
        ChatMessage msg = ChatMessage.builder().receiverId(999L).branchId(5L).build();
        service.assertCanAcknowledgeMessage(USER, msg);
        verifyNoInteractions(chatAuditService);
    }

    @Test
    void assertCanAcknowledgeMessage_directMessageNonRecipientDenied() {
        stubRole(USER, RoleType.USER);
        ChatMessage msg = ChatMessage.builder().id(3L).receiverId(999L).build();
        assertThatThrownBy(() -> service.assertCanAcknowledgeMessage(USER, msg))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatAuditService).accessDenied(eq(USER), eq("CHAT_MESSAGE"), eq(3L), any());
    }

    // ── department ────────────────────────────────────────────────────────────

    @Test
    void canAccessDepartment_adminBypassesMembership() {
        stubRole(ADMIN, RoleType.ADMIN);
        assertThat(service.canAccessDepartment(ADMIN, 4L)).isTrue();
        verify(userRepository, never()).existsByIdAndDepartments_Id(any(), any());
    }

    @Test
    void canAccessDepartment_memberAllowed_nonMemberDenied() {
        stubRole(USER, RoleType.USER);
        when(userRepository.existsByIdAndDepartments_Id(USER, 4L)).thenReturn(true);
        when(userRepository.existsByIdAndDepartments_Id(USER, 5L)).thenReturn(false);
        assertThat(service.canAccessDepartment(USER, 4L)).isTrue();
        assertThat(service.canAccessDepartment(USER, 5L)).isFalse();
    }

    // ── conversation ──────────────────────────────────────────────────────────

    private ChatConversation conversation(Long id, ChatConversationType type) {
        ChatConversation c = new ChatConversation();
        c.setId(id);
        c.setConversationType(type);
        return c;
    }

    @Test
    void canAccessConversation_directRequiresActiveMembership() {
        ChatConversation direct = conversation(1L, ChatConversationType.DIRECT);
        when(chatParticipantRepository.existsByConversationIdAndUserIdAndIsActiveTrue(1L, USER)).thenReturn(true);
        assertThat(service.canAccessConversation(USER, direct)).isTrue();

        ChatConversation other = conversation(2L, ChatConversationType.DIRECT);
        when(chatParticipantRepository.existsByConversationIdAndUserIdAndIsActiveTrue(2L, USER)).thenReturn(false);
        assertThat(service.canAccessConversation(USER, other)).isFalse();
    }

    @Test
    void canAccessConversation_branchDelegatesToBranchRule() {
        stubRole(USER, RoleType.USER);
        ChatConversation branch = conversation(3L, ChatConversationType.BRANCH);
        branch.setBranchId(7L);
        when(userRepository.existsByIdAndBranches_Id(USER, 7L)).thenReturn(true);
        assertThat(service.canAccessConversation(USER, branch)).isTrue();
    }

    @Test
    void canAccessConversationById_loadsAndChecks() {
        ChatConversation direct = conversation(9L, ChatConversationType.GROUP);
        when(chatConversationRepository.findById(9L)).thenReturn(Optional.of(direct));
        when(chatParticipantRepository.existsByConversationIdAndUserIdAndIsActiveTrue(9L, USER)).thenReturn(true);
        assertThat(service.canAccessConversation(USER, 9L)).isTrue();
        assertThat(service.canAccessConversation(USER, (Long) null)).isFalse();
    }

    @Test
    void assertCanCreateGroup_deniesNonAdmin_audits() {
        stubRole(USER, RoleType.USER);
        assertThatThrownBy(() -> service.assertCanCreateGroup(USER))
                .isInstanceOf(AccessDeniedException.class);
        verify(chatAuditService).accessDenied(eq(USER), eq("CHAT_CONVERSATION"), isNull(), any());
    }

    @Test
    void assertCanManageConversation_ownerAllowed_memberDenied() {
        ChatConversation group = conversation(4L, ChatConversationType.GROUP);
        stubRole(USER, RoleType.USER);
        com.nexmemo.core.entity.ChatParticipant owner = new com.nexmemo.core.entity.ChatParticipant();
        owner.setIsActive(true);
        owner.setParticipantRole(com.nexmemo.core.enums.ChatParticipantRole.OWNER);
        when(chatParticipantRepository.findByConversationIdAndUserId(4L, USER)).thenReturn(Optional.of(owner));
        service.assertCanManageConversation(USER, group);
        verifyNoInteractions(chatAuditService);
    }

    @Test
    void assertCanManageConversation_plainMemberDenied() {
        ChatConversation group = conversation(4L, ChatConversationType.GROUP);
        stubRole(USER, RoleType.USER);
        com.nexmemo.core.entity.ChatParticipant member = new com.nexmemo.core.entity.ChatParticipant();
        member.setIsActive(true);
        member.setParticipantRole(com.nexmemo.core.enums.ChatParticipantRole.MEMBER);
        when(chatParticipantRepository.findByConversationIdAndUserId(4L, USER)).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.assertCanManageConversation(USER, group))
                .isInstanceOf(AccessDeniedException.class);
    }
}
