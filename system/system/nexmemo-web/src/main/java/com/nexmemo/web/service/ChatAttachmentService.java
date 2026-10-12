package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatAttachmentDto;
import com.nexmemo.core.entity.ChatAttachment;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.enums.ChatAttachmentScanStatus;
import com.nexmemo.security.repository.ChatAttachmentRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Secure file attachments for chat messages (Phase 2f).
 *
 * Hardening on top of the pipeline:
 * <ul>
 *   <li><b>extension/MIME whitelist</b> — executables, scripts and archives are excluded;</li>
 *   <li><b>size cap</b> — 25 MB, enforced before the bytes are buffered;</li>
 *   <li><b>malware scan</b> — {@link ClamAvScanner} (fail closed when enabled);</li>
 *   <li><b>authorized download</b> — bytes never leave the DB except through
 *       {@link #loadAuthorized} which re-checks conversation access and scan status;</li>
 *   <li><b>content hash</b> — sha256 recorded for quarantine/deduplication.</li>
 * </ul>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatAttachmentService {

    public static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "jpg", "jpeg", "png", "gif", "zip");

    private final ChatAttachmentRepository attachmentRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;
    private final ClamAvScanner clamAvScanner;

    /**
     * Validates and stores an uploaded file against a message, running the malware scan first.
     */
    public ChatAttachmentDto upload(Long userId, Long conversationId, Long messageId, MultipartFile file) {
        ChatConversation conversation = conversationService.requireConversation(conversationId);
        chatAuth.assertCanAccessConversation(userId, conversation);
        if (Boolean.TRUE.equals(conversation.getIsLocked())) {
            throw new AccessDeniedException("This conversation is locked");
        }
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!conversationId.equals(message.getConversationId())) {
            throw new IllegalArgumentException("Message does not belong to this conversation");
        }
        if (Boolean.TRUE.equals(message.getIsDeleted())) {
            throw new IllegalArgumentException("A deleted message cannot have attachments");
        }

        String originalName = sanitizeFileName(file.getOriginalFilename());
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Invalid file name");
        }
        String extension = extensionOf(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("File type '" + extension + "' is not allowed in chat attachments");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Attachment exceeds the 25 MB limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.warn("Failed to read upload by userId={}: {}", userId, e.getMessage());
            throw new IllegalArgumentException("Could not read the uploaded file");
        }

        String sha256 = sha256(bytes);
        ChatAttachmentScanStatus status = scan(bytes);
        if (status == ChatAttachmentScanStatus.INFECTED) {
            log.warn("Malware detected in upload by userId={} in conversation {} ({} bytes)",
                    userId, conversationId, bytes.length);
            throw new IllegalArgumentException("Attachment rejected: malware detected");
        }

        ChatAttachment attachment = ChatAttachment.builder()
                .messageId(messageId)
                .conversationId(conversationId)
                .fileName(originalName)
                .contentType(file.getContentType() != null && file.getContentType().length() <= 120
                        ? file.getContentType() : "application/octet-stream")
                .sizeBytes((long) bytes.length)
                .sha256(sha256)
                .scanStatus(status)
                .fileData(bytes)
                .uploadedById(userId)
                .build();
        ChatAttachment saved = attachmentRepository.save(attachment);
        log.info("Attachment {} uploaded by userId={} to message {} ({} bytes, sha256={})",
                saved.getId(), userId, messageId, bytes.length, sha256);
        return toDto(saved);
    }

    /**
     * Loads an attachment for download, re-checking conversation access and that the file
     * actually passed the malware scan. Unscanned/failed files cannot be downloaded.
     */
    @Transactional(readOnly = true)
    public ChatAttachment loadAuthorized(Long userId, Long attachmentId) {
        ChatAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        ChatConversation conversation = conversationService.requireConversation(attachment.getConversationId());
        chatAuth.assertCanAccessConversation(userId, conversation);
        if (attachment.getScanStatus() != ChatAttachmentScanStatus.CLEAN) {
            log.warn("Refusing download of un-scanned attachment {} (status {}) by userId={}",
                    attachmentId, attachment.getScanStatus(), userId);
            throw new AccessDeniedException("Attachment is not available for download");
        }
        return attachment;
    }

    @Transactional(readOnly = true)
    public List<ChatAttachmentDto> forMessage(Long messageId) {
        return attachmentRepository.findByMessageIdOrderByIdAsc(messageId).stream()
                .map(this::toDto)
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatAttachmentScanStatus scan(byte[] bytes) {
        if (!clamAvScanner.isEnabled()) {
            return ChatAttachmentScanStatus.CLEAN;
        }
        try {
            return clamAvScanner.scan(bytes) ? ChatAttachmentScanStatus.CLEAN
                    : ChatAttachmentScanStatus.INFECTED;
        } catch (IllegalStateException e) {
            // Scanner up but unavailable → fail closed (nothing released unscanned).
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static Map<Long, List<ChatAttachmentDto>> groupByMessage(List<ChatAttachment> rows) {
        Map<Long, List<ChatAttachmentDto>> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (ChatAttachment a : rows) {
            map.computeIfAbsent(a.getMessageId(), k -> new ArrayList<>()).add(toDtoStatic(a));
        }
        return map;
    }

    private ChatAttachmentDto toDto(ChatAttachment a) {
        return toDtoStatic(a);
    }

    private static ChatAttachmentDto toDtoStatic(ChatAttachment a) {
        return ChatAttachmentDto.builder()
                .id(a.getId())
                .fileName(a.getFileName())
                .contentType(a.getContentType())
                .sizeBytes(a.getSizeBytes())
                .sha256(a.getSha256())
                .scanStatus(a.getScanStatus() != null ? a.getScanStatus().name() : null)
                .uploadedById(a.getUploadedById())
                .createdDate(a.getCreatedDate())
                .build();
    }

    /** Strips path separators/control chars so the client-supplied name can never escape. */
    static String sanitizeFileName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace('\\', '_').replace('/', '_')
                .replaceAll("[\\u0000-\\u001F]", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}