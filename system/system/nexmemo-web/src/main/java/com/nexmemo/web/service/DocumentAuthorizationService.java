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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Central, server-side authorization for the document vault.
 *
 * <p>SECURITY RULE: a document is <b>never</b> accessible merely because the caller knows its id or
 * storage path. Every read/write/share resolves the caller from the JWT principal and the resource
 * from the database, then evaluates ownership, administrator status and explicit grants.</p>
 *
 * <p>Privilege model (additive):</p>
 * <ul>
 *   <li><b>Owner</b> — full read/write/delete/share.</li>
 *   <li><b>Administrator</b> (ADMIN / SUPER_ADMIN) — full access.</li>
 *   <li><b>Explicit grant</b> — a {@code document_permission} row for the caller as a USER, or for
 *       one of their ROLE / BRANCH / DEPARTMENT memberships, with the specific capability flag.</li>
 * </ul>
 *
 * <p>A soft-deleted document is treated as non-existent for every capability.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAuthorizationService {

    private static final String ENTITY_DOCUMENT = "DOCUMENT";

    private final UserRepository userRepository;
    private final DocumentRecordRepository documentRecordRepository;
    private final DocumentPermissionRepository documentPermissionRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final ChatAuthorizationService chatAuth;

    /** Capabilities resolved for a caller on a single document. */
    public record Capabilities(boolean read, boolean write, boolean delete, boolean share) {
        static final Capabilities NONE = new Capabilities(false, false, false, false);
        static final Capabilities ALL = new Capabilities(true, true, true, true);
    }

    /** Pre-resolved subject ids used to build a readable-documents query. */
    public record AccessScope(Long userId, boolean admin, List<Long> roleIds,
                              List<Long> branchIds, List<Long> departmentIds,
                              List<Long> conversationIds) {
    }

    // ── Role checks ───────────────────────────────────────────────────────────

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

    // ── Query scope (for list/search) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public AccessScope scopeFor(Long userId) {
        boolean admin = isAdmin(userId);
        User user = userRepository.findByIdWithRole(userId).orElse(null);
        List<Long> roleIds = user != null && user.getRole() != null && user.getRole().getId() != null
                ? List.of(user.getRole().getId()) : List.of();
        List<Long> branchIds = user != null
                ? user.getBranches().stream().map(Branch::getId).filter(Objects::nonNull).toList()
                : List.of();
        List<Long> deptIds = user != null
                ? user.getDepartments().stream().map(Department::getId).filter(Objects::nonNull).toList()
                : List.of();
        List<Long> conversationIds = chatAuth.activeConversationIds(userId);
        return new AccessScope(userId, admin, roleIds, branchIds, deptIds, conversationIds);
    }

    // ── Per-document capabilities ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Capabilities capabilities(Long userId, DocumentRecord doc) {
        if (userId == null || doc == null || Boolean.TRUE.equals(doc.getIsDeleted())) {
            return Capabilities.NONE;
        }
        if (isAdmin(userId) || userId.equals(doc.getOwnerId())) {
            return Capabilities.ALL;
        }
        User user = userRepository.findByIdWithRole(userId).orElse(null);
        if (user == null) {
            return Capabilities.NONE;
        }
        List<Long> roleIds = user.getRole() != null && user.getRole().getId() != null
                ? List.of(user.getRole().getId()) : List.of();
        List<Long> branchIds = user.getBranches().stream().map(Branch::getId).toList();
        List<Long> deptIds = user.getDepartments().stream().map(Department::getId).toList();

        boolean read = false, write = false, delete = false, share = false;
        for (DocumentPermission p : documentPermissionRepository.findByDocumentId(doc.getId())) {
            if (matches(p, userId, roleIds, branchIds, deptIds)) {
                read |= Boolean.TRUE.equals(p.getCanRead());
                write |= Boolean.TRUE.equals(p.getCanWrite());
                delete |= Boolean.TRUE.equals(p.getCanDelete());
                share |= Boolean.TRUE.equals(p.getCanShare());
            }
        }
        return new Capabilities(read, write, delete, share);
    }

    private boolean matches(DocumentPermission p, Long userId, List<Long> roleIds,
                            List<Long> branchIds, List<Long> deptIds) {
        if (p.getSubjectType() == null || p.getSubjectId() == null) {
            return false;
        }
        return switch (p.getSubjectType()) {
            case USER -> Objects.equals(p.getSubjectId(), userId);
            case ROLE -> roleIds.contains(p.getSubjectId());
            case BRANCH -> branchIds.contains(p.getSubjectId());
            case DEPARTMENT -> deptIds.contains(p.getSubjectId());
            // Phase 5: a conversation grant is evaluated against the caller's live chat access, so
            // leaving the conversation (or it being locked) revokes the inherited read immediately.
            case CONVERSATION -> chatAuth.canAccessConversation(userId, p.getSubjectId());
        };
    }

    // ── Assertions (throw + audit) ────────────────────────────────────────────

    public void assertCanRead(Long userId, DocumentRecord doc) {
        if (!capabilities(userId, doc).read()) {
            deny(userId, doc, "read");
        }
    }

    public void assertCanWrite(Long userId, DocumentRecord doc) {
        if (!capabilities(userId, doc).write()) {
            deny(userId, doc, "write");
        }
    }

    public void assertCanDelete(Long userId, DocumentRecord doc) {
        if (!capabilities(userId, doc).delete()) {
            deny(userId, doc, "delete");
        }
    }

    public void assertCanShare(Long userId, DocumentRecord doc) {
        if (!capabilities(userId, doc).share()) {
            deny(userId, doc, "share");
        }
    }

    /** Loads a live document or throws a not-found style denial (audited). */
    @Transactional(readOnly = true)
    public DocumentRecord requireLiveDocument(Long userId, Long documentId) {
        DocumentRecord doc = documentId == null ? null : documentRecordRepository.findById(documentId).orElse(null);
        if (doc == null || Boolean.TRUE.equals(doc.getIsDeleted())) {
            deny(userId, doc, "access");
        }
        return doc;
    }

    private void deny(Long userId, DocumentRecord doc, String capability) {
        Long docId = doc == null ? null : doc.getId();
        log.warn("Document authorization denied: userId={} document#{} capability={}", userId, docId, capability);
        auditEventPublisher.accessDenied(userId, ENTITY_DOCUMENT, docId, "document " + capability + " denied");
        throw new AccessDeniedException("You are not authorized to perform this action on the requested document");
    }
}
