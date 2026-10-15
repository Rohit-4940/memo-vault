package com.nexmemo.web.service;

import com.nexmemo.core.entity.Branch;
import com.nexmemo.core.entity.Department;
import com.nexmemo.core.entity.DocumentPermission;
import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.PermissionSubjectType;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.core.repository.DocumentPermissionRepository;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.web.service.DocumentAuthorizationService.Capabilities;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the document access-control foundation (Phase 2). These assert that a document is
 * never accessible merely because its id is known, and that reads/writes/deletes/shares each require
 * the matching capability.
 */
@ExtendWith(MockitoExtension.class)
class DocumentAuthorizationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DocumentRecordRepository documentRecordRepository;
    @Mock private DocumentPermissionRepository documentPermissionRepository;
    @Mock private AuditEventPublisher auditEventPublisher;

    @InjectMocks private DocumentAuthorizationService service;

    private static final Long USER = 10L;
    private static final Long ADMIN = 1L;
    private static final Long OTHER = 999L;
    private static final Long DOC_ID = 100L;

    private User user(Long id, RoleType type, Long roleId) {
        Role role = new Role();
        role.setId(roleId);
        role.setRoleType(type);
        role.setRoleName(type.name());
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private DocumentRecord doc(Long ownerId) {
        DocumentRecord d = new DocumentRecord();
        d.setId(DOC_ID);
        d.setOwnerId(ownerId);
        d.setIsDeleted(false);
        return d;
    }

    private DocumentPermission perm(PermissionSubjectType type, Long subjectId,
                                    boolean read, boolean write, boolean delete, boolean share) {
        DocumentPermission p = new DocumentPermission();
        p.setDocumentId(DOC_ID);
        p.setSubjectType(type);
        p.setSubjectId(subjectId);
        p.setCanRead(read);
        p.setCanWrite(write);
        p.setCanDelete(delete);
        p.setCanShare(share);
        return p;
    }

    // ── isAdmin ───────────────────────────────────────────────────────────────

    @Test
    void isAdmin_trueForAdmin_falseForUserAndNull() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(user(ADMIN, RoleType.ADMIN, 1L)));
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));

        assertThat(service.isAdmin(ADMIN)).isTrue();
        assertThat(service.isAdmin(USER)).isFalse();
        assertThat(service.isAdmin(null)).isFalse();
    }

    // ── capabilities ──────────────────────────────────────────────────────────

    @Test
    void capabilities_ownerGetsEverything() {
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));
        Capabilities caps = service.capabilities(USER, doc(USER));
        assertThat(caps).isEqualTo(Capabilities.ALL);
    }

    @Test
    void capabilities_adminGetsEverythingWithoutPermissionLookup() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(user(ADMIN, RoleType.SUPER_ADMIN, 2L)));
        Capabilities caps = service.capabilities(ADMIN, doc(OTHER));
        assertThat(caps).isEqualTo(Capabilities.ALL);
        verify(documentPermissionRepository, never()).findByDocumentId(any());
    }

    @Test
    void capabilities_explicitUserGrantGrantsOnlyRead() {
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));
        when(documentPermissionRepository.findByDocumentId(DOC_ID))
                .thenReturn(List.of(perm(PermissionSubjectType.USER, USER, true, false, false, false)));

        Capabilities caps = service.capabilities(USER, doc(OTHER));
        assertThat(caps.read()).isTrue();
        assertThat(caps.write()).isFalse();
        assertThat(caps.delete()).isFalse();
        assertThat(caps.share()).isFalse();
    }

    @Test
    void capabilities_roleGrantMatchesCallersRole() {
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));
        when(documentPermissionRepository.findByDocumentId(DOC_ID))
                .thenReturn(List.of(perm(PermissionSubjectType.ROLE, 3L, true, true, false, false)));

        Capabilities caps = service.capabilities(USER, doc(OTHER));
        assertThat(caps.read()).isTrue();
        assertThat(caps.write()).isTrue();
        assertThat(caps.delete()).isFalse();
    }

    @Test
    void capabilities_branchGrantMatchesCallersBranch() {
        User u = user(USER, RoleType.USER, 3L);
        Branch branch = new Branch();
        branch.setId(5L);
        u.setBranches(List.of(branch));
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(u));
        when(documentPermissionRepository.findByDocumentId(DOC_ID))
                .thenReturn(List.of(perm(PermissionSubjectType.BRANCH, 5L, true, false, false, false)));

        assertThat(service.capabilities(USER, doc(OTHER)).read()).isTrue();
    }

    @Test
    void capabilities_departmentGrantMatchesCallersDepartment() {
        User u = user(USER, RoleType.USER, 3L);
        Department dept = new Department();
        dept.setId(7L);
        u.setDepartments(List.of(dept));
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(u));
        when(documentPermissionRepository.findByDocumentId(DOC_ID))
                .thenReturn(List.of(perm(PermissionSubjectType.DEPARTMENT, 7L, true, false, false, true)));

        Capabilities caps = service.capabilities(USER, doc(OTHER));
        assertThat(caps.read()).isTrue();
        assertThat(caps.share()).isTrue();
    }

    @Test
    void capabilities_nonMatchingGrantDeniesAll() {
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));
        when(documentPermissionRepository.findByDocumentId(DOC_ID))
                .thenReturn(List.of(perm(PermissionSubjectType.ROLE, 99L, true, true, true, true)));

        assertThat(service.capabilities(USER, doc(OTHER))).isEqualTo(Capabilities.NONE);
    }

    @Test
    void capabilities_deletedDocumentDeniesAll() {
        DocumentRecord deleted = doc(USER);
        deleted.setIsDeleted(true);
        assertThat(service.capabilities(USER, deleted)).isEqualTo(Capabilities.NONE);
        verify(documentPermissionRepository, never()).findByDocumentId(any());
    }

    @Test
    void capabilities_nullUserOrDocDeniesAll() {
        assertThat(service.capabilities(null, doc(USER))).isEqualTo(Capabilities.NONE);
        assertThat(service.capabilities(USER, null)).isEqualTo(Capabilities.NONE);
    }

    // ── assertions ────────────────────────────────────────────────────────────

    @Test
    void assertCanRead_throwsAndAuditsWhenDenied() {
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));
        when(documentPermissionRepository.findByDocumentId(DOC_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.assertCanRead(USER, doc(OTHER)))
                .isInstanceOf(AccessDeniedException.class);
        verify(auditEventPublisher).accessDenied(eq(USER), eq("DOCUMENT"), eq(DOC_ID), any());
    }

    @Test
    void assertCanWrite_deniedForReadOnlyGrant() {
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(user(USER, RoleType.USER, 3L)));
        when(documentPermissionRepository.findByDocumentId(DOC_ID))
                .thenReturn(List.of(perm(PermissionSubjectType.USER, USER, true, false, false, false)));

        assertThatThrownBy(() -> service.assertCanWrite(USER, doc(OTHER)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireLiveDocument_missingOrDeletedThrows() {
        when(documentRecordRepository.findById(DOC_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireLiveDocument(USER, DOC_ID))
                .isInstanceOf(AccessDeniedException.class);

        DocumentRecord deleted = doc(USER);
        deleted.setIsDeleted(true);
        when(documentRecordRepository.findById(DOC_ID)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.requireLiveDocument(USER, DOC_ID))
                .isInstanceOf(AccessDeniedException.class);
    }
}
