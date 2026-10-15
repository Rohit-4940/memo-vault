package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMessageDocumentDto;
import com.nexmemo.core.dto.ShareDocumentRequest;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.ChatMessageDocument;
import com.nexmemo.core.entity.DocumentPermission;
import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.PermissionSubjectType;
import com.nexmemo.core.repository.DocumentPermissionRepository;
import com.nexmemo.core.repository.DocumentRecordRepository;
import com.nexmemo.security.repository.ChatMessageDocumentRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.web.config.DocumentProperties;
import com.nexmemo.web.exception.DocumentPolicyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shares canonical vault documents into chat conversations and renders them permission-aware
 * (Enterprise Phase 5).
 *
 * <p>Design rules:</p>
 * <ul>
 *   <li><b>No byte copies.</b> A message references the vault {@link DocumentRecord}; every preview or
 *       download re-resolves the caller's read capability, so revoking or deleting the document — or
 *       a recipient leaving the conversation — immediately changes what is visible.</li>
 *   <li><b>Explicit, revocable grant.</b> By default a share creates a {@code CONVERSATION} read
 *       grant for the whole conversation (evaluated against live chat membership). Revoking the last
 *       share of a document in a conversation removes that grant.</li>
 *   <li><b>Policy-gated.</b> RESTRICTED and CONFIDENTIAL documents cannot be shared into a
 *       conversation unless the vault sharing policy is explicitly relaxed.</li>
 *   <li><b>Least disclosure.</b> A recipient without read capability sees a "restricted" card with
 *       no document id or metadata.</li>
 * </ul>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatDocumentService {

    private static final String ENTITY = "CHAT_MESSAGE_DOCUMENT";

    private final ChatMessageDocumentRepository shareRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentRecordRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;
    private final DocumentAuthorizationService documentAuthz;
    private final DocumentProperties documentProperties;
    private final AuditEventPublisher auditEventPublisher;

    // ── Share ─────────────────────────────────────────────────────────────────

    public ChatMessageDocumentDto share(Long userId, Long conversationId, Long messageId,
                                        ShareDocumentRequest request) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        if (Boolean.TRUE.equals(conversation.getIsLocked())) {
            throw new AccessDeniedException("This conversation is locked");
        }
        ChatMessage message = requireConversationMessage(conversationId, messageId);
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new IllegalArgumentException("A deleted message cannot have documents");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new IllegalArgumentException("documentId is required");
        }

        DocumentRecord doc = documentAuthz.requireLiveDocument(userId, request.getDocumentId());
        documentAuthz.assertCanShare(userId, doc);
        assertChatSharingAllowed(doc);

        // Idempotency: sharing the same document onto the same message twice returns the existing row.
        var existing = shareRepository
                .findByMessageIdAndDocumentIdAndIsRevokedFalse(messageId, doc.getId());
        if (existing.isPresent()) {
            return toDto(userId, documentAuthz.isAdmin(userId), existing.get());
        }

        boolean grant = request.getGrantAccess() == null || Boolean.TRUE.equals(request.getGrantAccess());
        if (grant) {
            upsertConversationGrant(doc.getId(), conversationId, userId);
        }

        ChatMessageDocument row = ChatMessageDocument.builder()
                .messageId(messageId)
                .conversationId(conversationId)
                .documentId(doc.getId())
                .documentVersionNo(doc.getCurrentVersion())
                .documentTitle(doc.getTitle() != null && !doc.getTitle().isBlank()
                        ? doc.getTitle() : doc.getOriginalFilename())
                .originalFilename(doc.getOriginalFilename())
                .contentType(doc.getContentType())
                .sizeBytes(doc.getSizeBytes())
                .sha256(doc.getSha256())
                .classification(doc.getClassification())
                .note(trimToNull(request.getNote(), 500))
                .sharedById(userId)
                .grantedRead(grant)
                .isRevoked(false)
                .build();
        ChatMessageDocument saved = shareRepository.save(row);

        auditEventPublisher.recordChange(userId, "CHAT_DOCUMENT_SHARE", ENTITY, saved.getId(),
                null, Map.of("documentId", doc.getId(), "conversationId", conversationId,
                        "grantedRead", grant));
        log.info("Document {} shared into conversation {} on message {} by userId={} (grant={})",
                doc.getId(), conversationId, messageId, userId, grant);
        return toDto(userId, documentAuthz.isAdmin(userId), saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessageDocumentDto> listForMessage(Long userId, Long conversationId, Long messageId) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        requireConversationMessage(conversationId, messageId);
        return renderRows(userId, shareRepository.findByMessageIdAndIsRevokedFalseOrderByIdAsc(messageId));
    }

    /**
     * Bulk renderer used when mapping a page of messages. Only rows whose message actually carries a
     * document are resolved, so history/search rendering stays cheap for document-free conversations.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ChatMessageDocumentDto>> renderForMessages(Long viewerId, List<ChatMessage> messages) {
        Map<Long, List<ChatMessageDocumentDto>> map = new LinkedHashMap<>();
        if (messages == null || messages.isEmpty()) {
            return map;
        }
        List<Long> ids = messages.stream().map(ChatMessage::getId).toList();
        List<ChatMessageDocument> rows = shareRepository.findByMessageIdInAndIsRevokedFalseOrderByIdAsc(ids);
        if (rows.isEmpty()) {
            return map;
        }
        boolean admin = documentAuthz.isAdmin(viewerId);
        for (ChatMessageDocument row : rows) {
            map.computeIfAbsent(row.getMessageId(), k -> new ArrayList<>())
                    .add(toDto(viewerId, admin, row));
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDocumentDto> renderForMessage(Long viewerId, Long messageId) {
        List<ChatMessageDocument> rows = shareRepository.findByMessageIdAndIsRevokedFalseOrderByIdAsc(messageId);
        return rows.isEmpty() ? List.of() : renderRows(viewerId, rows);
    }

    // ── Revoke ────────────────────────────────────────────────────────────────

    public void revoke(Long userId, Long shareId) {
        ChatMessageDocument row = shareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        ChatConversation conversation = conversationService.requireConversation(row.getConversationId());
        chatAuth.assertCanAccessConversation(userId, conversation);

        DocumentRecord doc = documentRepository.findById(row.getDocumentId())
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted())).orElse(null);
        boolean canRevoke = userId.equals(row.getSharedById())
                || documentAuthz.isAdmin(userId)
                || (doc != null && documentAuthz.capabilities(userId, doc).write());
        if (!canRevoke) {
            throw new AccessDeniedException("Only the sharer, a document editor or an administrator can revoke this share");
        }
        if (Boolean.TRUE.equals(row.getIsRevoked())) {
            return; // idempotent
        }
        row.setIsRevoked(true);
        shareRepository.save(row);

        // Withdraw the conversation grant once no active share of this document remains.
        if (Boolean.TRUE.equals(row.getGrantedRead())
                && shareRepository.countByDocumentIdAndConversationIdAndIsRevokedFalse(
                        row.getDocumentId(), row.getConversationId()) == 0) {
            permissionRepository
                    .findByDocumentIdAndSubjectTypeAndSubjectId(row.getDocumentId(),
                            PermissionSubjectType.CONVERSATION, row.getConversationId())
                    .ifPresent(permissionRepository::delete);
        }

        auditEventPublisher.recordChange(userId, "CHAT_DOCUMENT_REVOKE", ENTITY, shareId,
                Map.of("isRevoked", false), Map.of("isRevoked", true));
        log.info("Document share {} revoked by userId={}", shareId, userId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private List<ChatMessageDocumentDto> renderRows(Long viewerId, List<ChatMessageDocument> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        boolean admin = documentAuthz.isAdmin(viewerId);
        List<ChatMessageDocumentDto> out = new ArrayList<>(rows.size());
        for (ChatMessageDocument row : rows) {
            out.add(toDto(viewerId, admin, row));
        }
        return out;
    }

    private ChatMessageDocumentDto toDto(Long viewerId, boolean admin, ChatMessageDocument row) {
        DocumentRecord doc = documentRepository.findById(row.getDocumentId())
                .filter(d -> !Boolean.TRUE.equals(d.getIsDeleted())).orElse(null);
        boolean readable = doc != null && documentAuthz.capabilities(viewerId, doc).read();

        ChatMessageDocumentDto.ChatMessageDocumentDtoBuilder builder = ChatMessageDocumentDto.builder()
                .id(row.getId())
                .messageId(row.getMessageId())
                .conversationId(row.getConversationId())
                .note(row.getNote())
                .sharedById(row.getSharedById())
                .grantedRead(Boolean.TRUE.equals(row.getGrantedRead()))
                .isRevoked(Boolean.TRUE.equals(row.getIsRevoked()))
                .restricted(!readable)
                .canRevoke(admin || viewerId.equals(row.getSharedById()))
                .createdDate(row.getCreatedDate());

        if (readable) {
            builder.documentId(row.getDocumentId())
                    .documentVersionNo(row.getDocumentVersionNo())
                    .title(row.getDocumentTitle())
                    .originalFilename(row.getOriginalFilename())
                    .contentType(row.getContentType())
                    .sizeBytes(row.getSizeBytes())
                    .classification(row.getClassification() != null ? row.getClassification().name() : null)
                    .sha256(row.getSha256());
        }
        return builder.build();
    }

    private void upsertConversationGrant(Long documentId, Long conversationId, Long grantedBy) {
        DocumentPermission permission = permissionRepository
                .findByDocumentIdAndSubjectTypeAndSubjectId(documentId,
                        PermissionSubjectType.CONVERSATION, conversationId)
                .orElseGet(DocumentPermission::new);
        permission.setDocumentId(documentId);
        permission.setSubjectType(PermissionSubjectType.CONVERSATION);
        permission.setSubjectId(conversationId);
        permission.setCanRead(true);
        permission.setCanWrite(false);
        permission.setCanDelete(false);
        permission.setCanShare(false);
        permission.setGrantedById(grantedBy);
        permissionRepository.save(permission);
    }

    private void assertChatSharingAllowed(DocumentRecord doc) {
        DocumentClassification c = doc.getClassification();
        if (c == DocumentClassification.RESTRICTED && !documentProperties.getShare().isAllowRestricted()) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN,
                    "Sharing RESTRICTED documents into a conversation is disabled by policy");
        }
        if (c == DocumentClassification.CONFIDENTIAL && !documentProperties.getShare().isAllowConfidential()) {
            throw new DocumentPolicyException(HttpStatus.FORBIDDEN,
                    "Sharing CONFIDENTIAL documents into a conversation is disabled by policy");
        }
    }

    private ChatMessage requireConversationMessage(Long conversationId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!conversationId.equals(message.getConversationId())) {
            throw new IllegalArgumentException("Message does not belong to this conversation");
        }
        return message;
    }

    private static String trimToNull(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
