package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.ChatAttachmentDto;
import com.nexmemo.core.dto.ChatBookmarkDto;
import com.nexmemo.core.dto.ChatConversationSummaryDto;
import com.nexmemo.core.dto.ChatMemberDto;
import com.nexmemo.core.dto.ChatMessageDto;
import com.nexmemo.core.dto.ChatMentionDto;
import com.nexmemo.core.dto.ChatPresenceDto;
import com.nexmemo.core.dto.ChatReactionSummaryDto;
import com.nexmemo.core.dto.ChatReportDto;
import com.nexmemo.core.dto.CreateConversationRequest;
import com.nexmemo.core.dto.EditMessageRequest;
import com.nexmemo.core.dto.ForwardMessageRequest;
import com.nexmemo.core.dto.MessageStatusDto;
import com.nexmemo.core.dto.NotificationSettingRequest;
import com.nexmemo.core.dto.ReactionRequest;
import com.nexmemo.core.dto.SendMessageRequest;
import com.nexmemo.core.entity.ChatAttachment;
import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.web.service.ChatAttachmentService;
import com.nexmemo.web.service.ChatBookmarkService;
import com.nexmemo.web.service.ChatConversationService;
import com.nexmemo.web.service.ChatMentionService;
import com.nexmemo.web.service.ChatMessageService;
import com.nexmemo.web.service.ChatPresenceService;
import com.nexmemo.web.service.ChatReactionService;
import com.nexmemo.web.service.ChatSearchService;
import com.nexmemo.web.service.ChatTypingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Conversation-centric chat API (Phase 1a → 2j).
 *
 * The caller is always derived from the JWT principal; conversation access is authorized
 * server-side for every operation. Beyond core messaging this controller exposes:
 * threads (2c), pin/bookmark/mark-unread (2d), forwarding (2e), secure attachments (2f),
 * typing/presence (2g), notification preferences (2h), scoped search (2i) and the admin
 * moderation surface (2j).
 *
 * @author : NexMemo
 **/
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatConversationController {

    private final ChatConversationService conversationService;
    private final ChatMessageService messageService;
    private final ChatReactionService reactionService;
    private final ChatMentionService mentionService;
    private final ChatBookmarkService bookmarkService;
    private final ChatAttachmentService attachmentService;
    private final ChatTypingService typingService;
    private final ChatPresenceService presenceService;
    private final ChatSearchService searchService;

    // ── Conversations ─────────────────────────────────────────────────────────

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ChatConversation>> createConversation(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Conversation ready",
                conversationService.createOrGet(userId, request)));
    }

    @PostMapping("/conversations/memo/{memoId}")
    public ResponseEntity<ApiResponse<ChatConversation>> getOrCreateMemoConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long memoId) {
        CreateConversationRequest request = new CreateConversationRequest();
        request.setConversationType("MEMO");
        request.setMemoId(memoId);
        return ResponseEntity.ok(ApiResponse.success("Conversation ready",
                conversationService.createOrGet(userId, request)));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ChatConversationSummaryDto>>> listConversations(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(conversationService.listMyConversations(userId)));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<ChatConversation>> getConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(conversationService.getConversation(userId, id)));
    }

    @PostMapping("/conversations/{id}/participants")
    public ResponseEntity<ApiResponse<Void>> addParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        conversationService.addParticipant(userId, id, toLong(body.get("userId")));
        return ResponseEntity.ok(ApiResponse.success("Participant added", null));
    }

    @DeleteMapping("/conversations/{id}/participants/{targetUserId}")
    public ResponseEntity<ApiResponse<Void>> removeParticipant(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long targetUserId) {
        conversationService.removeParticipant(userId, id, targetUserId);
        return ResponseEntity.ok(ApiResponse.success("Participant removed", null));
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Message sent",
                messageService.send(userId, id, request)));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> getHistory(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getHistory(userId, id, beforeId, size)));
    }

    @PutMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessageDto>> editMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Message updated",
                messageService.editMessage(userId, id, messageId, request)));
    }

    @DeleteMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessageDto>> deleteMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(ApiResponse.success("Message deleted",
                messageService.deleteMessage(userId, id, messageId, reason)));
    }

    @GetMapping("/conversations/{id}/messages/{messageId}/status")
    public ResponseEntity<ApiResponse<MessageStatusDto>> getMessageStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(messageService.getMessageStatus(userId, messageId)));
    }

    // ── Phase 2c: threads ─────────────────────────────────────────────────────

    @GetMapping("/conversations/{id}/messages/{messageId}/replies")
    public ResponseEntity<ApiResponse<List<ChatMessageDto>>> getReplies(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getReplies(userId, id, messageId, afterId, size)));
    }

    // ── Phase 2d: pin / bookmark / mark-unread ────────────────────────────────

    @GetMapping("/conversations/{id}/pinned")
    public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> getPinned(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(messageService.getPinned(userId, id, size)));
    }

    /** Toggles pin state (author/admin only). POST pins, POST again unpins. */
    @PostMapping("/conversations/{id}/messages/{messageId}/pin")
    public ResponseEntity<ApiResponse<ChatMessageDto>> togglePin(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success("Pin state toggled",
                messageService.pinMessage(userId, id, messageId)));
    }

    @PostMapping("/messages/{messageId}/bookmark")
    public ResponseEntity<ApiResponse<ChatBookmarkDto>> bookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success("Message saved",
                bookmarkService.add(userId, messageId)));
    }

    @DeleteMapping("/messages/{messageId}/bookmark")
    public ResponseEntity<ApiResponse<Void>> unbookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId) {
        bookmarkService.remove(userId, messageId);
        return ResponseEntity.ok(ApiResponse.success("Bookmark removed", null));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<List<ChatBookmarkDto>>> myBookmarks(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(bookmarkService.list(userId, size)));
    }

    @PostMapping("/conversations/{id}/unread")
    public ResponseEntity<ApiResponse<Void>> markUnread(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        conversationService.markUnread(userId, id,
                body == null ? null : toLong(body.get("messageId")));
        return ResponseEntity.ok(ApiResponse.success("Marked as unread", null));
    }

    // ── Phase 2e: forwarding ──────────────────────────────────────────────────

    @PostMapping("/messages/{messageId}/forward")
    public ResponseEntity<ApiResponse<ChatMessageDto>> forwardMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId,
            @Valid @RequestBody ForwardMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Message forwarded",
                messageService.forwardMessage(userId, messageId, request)));
    }

    // ── Phase 2f: secure attachments ──────────────────────────────────────────

    @PostMapping(value = "/conversations/{id}/messages/{messageId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatAttachmentDto>> uploadAttachment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId,
            @RequestParam("file") MultipartFile file) {
        ChatAttachmentDto dto = attachmentService.upload(userId, id, messageId, file);
        messageService.broadcastMessageAfterAttachment(userId, messageId);
        return ResponseEntity.ok(ApiResponse.success("Attachment uploaded", dto));
    }

    @GetMapping("/conversations/{id}/messages/{messageId}/attachments")
    public ResponseEntity<ApiResponse<List<ChatAttachmentDto>>> listAttachments(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.forMessage(messageId)));
    }

    @GetMapping("/attachments/{attachmentId}/download")
    public ResponseEntity<ByteArrayResource> downloadAttachment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long attachmentId) {
        ChatAttachment attachment = attachmentService.loadAuthorized(userId, attachmentId);
        ByteArrayResource resource = new ByteArrayResource(attachment.getFileData() == null
                ? new byte[0] : attachment.getFileData());
        return ResponseEntity.ok()
                .contentType(resolveMediaType(attachment.getContentType()))
                .contentLength(resource.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    // ── Phase 2g: typing / presence ───────────────────────────────────────────

    @PostMapping("/conversations/{id}/typing")
    public ResponseEntity<ApiResponse<Void>> typing(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        typingService.notifyTyping(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Typing event sent", null));
    }

    @GetMapping("/conversations/{id}/presence")
    public ResponseEntity<ApiResponse<List<ChatPresenceDto>>> presence(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                presenceService.conversationPresence(userId, id)));
    }

    // ── Phase 2h: notification settings ───────────────────────────────────────

    @GetMapping("/conversations/{id}/notification-setting")
    public ResponseEntity<ApiResponse<String>> getNotificationSetting(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                conversationService.getNotificationSetting(userId, id)));
    }

    @PutMapping("/conversations/{id}/notification-setting")
    public ResponseEntity<ApiResponse<String>> updateNotificationSetting(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody NotificationSettingRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Notification level updated",
                conversationService.updateNotificationSetting(userId, id, request.getLevel())));
    }

    // ── Phase 2j: moderation / members ────────────────────────────────────────

    /** Manager (or admin) locks/unlocks a conversation. Locked = read-only for everyone. */
    @PostMapping("/conversations/{id}/lock")
    public ResponseEntity<ApiResponse<ChatConversation>> setLocked(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(ApiResponse.success("Lock state updated",
                conversationService.setLocked(userId, id,
                        Boolean.TRUE.equals(body.get("locked")))));
    }

    @GetMapping("/conversations/{id}/members")
    public ResponseEntity<ApiResponse<List<ChatMemberDto>>> listMembers(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(conversationService.listMembers(userId, id)));
    }

    // ── Read / delivery ─────────────────────────────────────────────────────────

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        conversationService.markRead(userId, id, toLong(body.get("lastReadMessageId")));
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PostMapping("/conversations/{id}/delivered")
    public ResponseEntity<ApiResponse<Void>> markDelivered(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        conversationService.markDelivered(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Marked as delivered", null));
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<ChatMessageDto>> getMessage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(messageService.getMessage(userId, messageId)));
    }

    // ── Reactions ─────────────────────────────────────────────────────────────

    @PostMapping("/messages/{messageId}/reactions")
    public ResponseEntity<ApiResponse<List<ChatReactionSummaryDto>>> addReaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId,
            @Valid @RequestBody ReactionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reaction added",
                reactionService.addReaction(userId, messageId, request.getEmoji())));
    }

    @DeleteMapping("/messages/{messageId}/reactions")
    public ResponseEntity<ApiResponse<List<ChatReactionSummaryDto>>> removeReaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId,
            @RequestParam String emoji) {
        return ResponseEntity.ok(ApiResponse.success("Reaction removed",
                reactionService.removeReaction(userId, messageId, emoji)));
    }

    @GetMapping("/messages/{messageId}/reactions")
    public ResponseEntity<ApiResponse<List<ChatReactionSummaryDto>>> listReactions(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.success(reactionService.listReactions(userId, messageId)));
    }

    // ── Mentions ──────────────────────────────────────────────────────────────

    /** The caller's own "tagged" inbox (mentions addressed to the principal). */
    @GetMapping("/mentions")
    public ResponseEntity<ApiResponse<List<ChatMentionDto>>> myMentions(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(mentionService.getMyMentions(userId, size)));
    }

    // ── Phase 2i: scoped search ───────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(searchService.search(userId, q, page, size)));
    }

    // ── Phase 2j: admin moderation queue ──────────────────────────────────────

    @GetMapping("/admin/reports")
    public ResponseEntity<ApiResponse<Page<ChatReportDto>>> adminReports(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(searchService.adminReports(userId, page, size)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            MediaType parsed = MediaType.parseMediaType(contentType);
            // Never render executable-ish types inline; always force attachment download.
            return parsed;
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}