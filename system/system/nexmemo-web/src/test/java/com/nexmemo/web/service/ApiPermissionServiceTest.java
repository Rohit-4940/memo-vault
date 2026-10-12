package com.nexmemo.web.service;

import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.RolePermissionRights;
import com.nexmemo.core.entity.UrlApi;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.security.repository.RolePermissionRightsRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for fine-grained role→URL permission resolution (Phase 3). Verifies the
 * "enforce once configured, fail open when unconfigured" contract.
 */
@ExtendWith(MockitoExtension.class)
class ApiPermissionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RolePermissionRightsRepository rolePermissionRightsRepository;

    @InjectMocks private ApiPermissionService service;

    private static final Long USER = 10L;
    private static final Long ADMIN = 1L;
    private static final Long ROLE_ID = 5L;

    private User userWithRole(Long id, Long roleId, RoleType type) {
        Role role = new Role();
        role.setId(roleId);
        role.setRoleType(type);
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private RolePermissionRights rightsWith(List<UrlApi> apis) {
        RolePermissionRights rights = new RolePermissionRights();
        rights.setApiRights(apis);
        return rights;
    }

    private UrlApi api(String url, String method) {
        UrlApi a = new UrlApi();
        a.setUrl(url);
        a.setMethod(method);
        return a;
    }

    // ── isAdmin ───────────────────────────────────────────────────────────────

    @Test
    void isAdmin_trueForAdmin_falseForUser() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(userWithRole(ADMIN, 1L, RoleType.ADMIN)));
        assertThat(service.isAdmin(ADMIN)).isTrue();

        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        assertThat(service.isAdmin(USER)).isFalse();
        assertThat(service.isAdmin(null)).isFalse();
    }

    // ── hasApiAccess ──────────────────────────────────────────────────────────

    @Test
    void hasApiAccess_nullUserDenied() {
        assertThat(service.hasApiAccess(null, "GET", "/memos")).isFalse();
    }

    @Test
    void hasApiAccess_blankPathAllowed() {
        assertThat(service.hasApiAccess(USER, "GET", " ")).isTrue();
        verifyNoInteractions(userRepository, rolePermissionRightsRepository);
    }

    @Test
    void hasApiAccess_adminBypassesMatrix() {
        when(userRepository.findByIdWithRole(ADMIN))
                .thenReturn(Optional.of(userWithRole(ADMIN, 1L, RoleType.ADMIN)));

        assertThat(service.hasApiAccess(ADMIN, "DELETE", "/anything")).isTrue();
        verifyNoInteractions(rolePermissionRightsRepository);
    }

    @Test
    void hasApiAccess_unconfiguredRoleFailsOpen() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID)).thenReturn(List.of(rightsWith(List.of())));

        assertThat(service.hasApiAccess(USER, "GET", "/memos")).isTrue();
    }

    @Test
    void hasApiAccess_configuredRole_allowsMatchingUrlAndMethod() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID))
                .thenReturn(List.of(rightsWith(List.of(api("/memos", "GET")))));

        assertThat(service.hasApiAccess(USER, "GET", "/memos")).isTrue();
    }

    @Test
    void hasApiAccess_configuredRole_allowsAntPattern() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID))
                .thenReturn(List.of(rightsWith(List.of(api("/memos/**", "GET")))));

        assertThat(service.hasApiAccess(USER, "GET", "/memos/42/history")).isTrue();
    }

    @Test
    void hasApiAccess_configuredRole_wildcardMethodAllows() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID))
                .thenReturn(List.of(rightsWith(List.of(api("/memos/**", "*")))));

        assertThat(service.hasApiAccess(USER, "POST", "/memos/42/approve")).isTrue();
    }

    @Test
    void hasApiAccess_configuredRole_deniesWrongMethod() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID))
                .thenReturn(List.of(rightsWith(List.of(api("/memos", "GET")))));

        assertThat(service.hasApiAccess(USER, "DELETE", "/memos")).isFalse();
    }

    @Test
    void hasApiAccess_configuredRole_deniesUnlistedUrl() {
        when(userRepository.findByIdWithRole(USER))
                .thenReturn(Optional.of(userWithRole(USER, ROLE_ID, RoleType.USER)));
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID))
                .thenReturn(List.of(rightsWith(List.of(api("/memos", "GET")))));

        assertThat(service.hasApiAccess(USER, "GET", "/admin/users")).isFalse();
    }

    @Test
    void hasApiAccess_userWithoutRoleDenied() {
        User noRole = new User();
        noRole.setId(USER);
        when(userRepository.findByIdWithRole(USER)).thenReturn(Optional.of(noRole));

        assertThat(service.hasApiAccess(USER, "GET", "/memos")).isFalse();
    }

    // ── caching ───────────────────────────────────────────────────────────────

    @Test
    void resolveRoleApis_cachesWithinTtl() {
        when(rolePermissionRightsRepository.findByRoleId(ROLE_ID))
                .thenReturn(List.of(rightsWith(List.of(api("/memos", "GET")))));

        service.resolveRoleApis(ROLE_ID);
        service.resolveRoleApis(ROLE_ID);

        verify(rolePermissionRightsRepository, times(1)).findByRoleId(ROLE_ID);

        service.evictCache();
        service.resolveRoleApis(ROLE_ID);
        verify(rolePermissionRightsRepository, times(2)).findByRoleId(ROLE_ID);
    }

    @Test
    void resolveRoleApis_nullRoleReturnsEmpty() {
        assertThat(service.resolveRoleApis(null)).isEmpty();
        verify(rolePermissionRightsRepository, never()).findByRoleId(any());
    }
}
