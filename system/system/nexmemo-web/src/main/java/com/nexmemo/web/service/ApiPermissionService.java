package com.nexmemo.web.service;

import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.RolePermissionRights;
import com.nexmemo.core.entity.UrlApi;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.security.repository.RolePermissionRightsRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves fine-grained API access from the previously unused RBAC tables
 * ({@code role_permission_rights} → {@code role_permission_api} → {@code url_api}).
 *
 * <p>Semantics — "enforced once configured":</p>
 * <ul>
 *   <li>Admins (ADMIN / SUPER_ADMIN) always pass.</li>
 *   <li>If a role has <b>no</b> URL/API rights configured, the request is allowed. This keeps the
 *       application working until an administrator explicitly maps URLs to a role in the
 *       permission matrix, so turning this on can never lock every user out of an unconfigured
 *       deployment.</li>
 *   <li>If a role <b>does</b> have API rights, only the configured method/URL combinations are
 *       allowed; everything else is denied.</li>
 * </ul>
 *
 * <p>URL patterns are matched with {@link AntPathMatcher} (e.g. {@code /memos/**}); the HTTP method
 * on the {@link UrlApi} may be {@code *}/blank to mean "any method".</p>
 *
 * <p>Resolved role rights are cached for a short TTL to avoid a query on every request. Call
 * {@link #evictCache()} after changing role permissions.</p>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiPermissionService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final UserRepository userRepository;
    private final RolePermissionRightsRepository rolePermissionRightsRepository;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<Long, CachedApis> cache = new ConcurrentHashMap<>();

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

    /**
     * Authoritative fine-grained API access decision. Runs in a single read-only transaction so the
     * lazy {@code apiRights} collection can be resolved safely.
     */
    @Transactional(readOnly = true)
    public boolean hasApiAccess(Long userId, String httpMethod, String servletPath) {
        if (userId == null) {
            return false;
        }
        // No path (e.g. root) → nothing to enforce.
        if (servletPath == null || servletPath.isBlank()) {
            return true;
        }
        if (isAdmin(userId)) {
            return true;
        }
        User user = userRepository.findByIdWithRole(userId).orElse(null);
        if (user == null || user.getRole() == null) {
            return false;
        }
        List<UrlApi> apis = resolveRoleApis(user.getRole().getId());
        if (apis.isEmpty()) {
            return true; // not configured → fail open, preserves existing behaviour
        }
        String method = httpMethod != null ? httpMethod : "";
        return apis.stream().anyMatch(api -> matches(api, method, servletPath));
    }

    @Transactional(readOnly = true)
    public List<UrlApi> resolveRoleApis(Long roleId) {
        if (roleId == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        CachedApis cached = cache.get(roleId);
        if (cached != null && cached.expiresAt > now) {
            return cached.apis;
        }
        List<UrlApi> apis = new ArrayList<>();
        for (RolePermissionRights rights : rolePermissionRightsRepository.findByRoleId(roleId)) {
            if (rights.getApiRights() != null) {
                apis.addAll(rights.getApiRights());
            }
        }
        cache.put(roleId, new CachedApis(List.copyOf(apis), now + CACHE_TTL_MS));
        return List.copyOf(apis);
    }

    /** Clears the role→API cache (call after role permissions change). */
    public void evictCache() {
        cache.clear();
    }

    private boolean matches(UrlApi api, String httpMethod, String servletPath) {
        String configuredMethod = api.getMethod();
        if (configuredMethod != null && !configuredMethod.isBlank()
                && !"*".equals(configuredMethod)
                && !configuredMethod.equalsIgnoreCase(httpMethod)) {
            return false;
        }
        String url = api.getUrl();
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.equals(servletPath) || pathMatcher.match(url, servletPath);
    }

    private static final class CachedApis {
        final List<UrlApi> apis;
        final long expiresAt;

        CachedApis(List<UrlApi> apis, long expiresAt) {
            this.apis = apis;
            this.expiresAt = expiresAt;
        }
    }
}
