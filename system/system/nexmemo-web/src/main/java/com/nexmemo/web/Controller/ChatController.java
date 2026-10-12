package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.ChatUserDto;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.web.service.ChatAuthorizationService;
import com.nexmemo.web.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat REST API.
 *
 * SECURITY: every endpoint resolves the caller from the JWT principal and delegates to
 * {@link ChatAuthorizationService} before touching a resource. The client-supplied
 * receiverId / memoId / branchId are never trusted on their own.
 *
 * @author : NexMemo
 **/
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private static final int MAX_MESSAGE_LENGTH = 5000;

    private final ChatService chatService;
    private final ChatAuthorizationService chatAuth;
    private final UserRepository userRepository;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(
            @AuthenticationPrincipal Long senderId,
            @RequestBody Map<String, Object> body) {
        Long receiverId = toLong(body.get("receiverId"));
        Long memoId = toLong(body.get("memoId"));
        Long branchId = toLong(body.get("branchId"));
        String message = body.get("message") != null ? body.get("message").toString() : null;

        if (senderId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Unauthenticated");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message exceeds maximum length of " + MAX_MESSAGE_LENGTH);
        }

        String conversationType = resolveConversationType(body.get("conversationType"), branchId, memoId, receiverId);

        switch (conversationType) {
            case "BRANCH_CHAT" -> chatAuth.assertCanAccessBranch(senderId, branchId);
            case "MEMO" -> chatAuth.assertCanAccessMemo(senderId, memoId);
            case "DIRECT_MESSAGE" -> chatAuth.assertCanDirectMessage(senderId, receiverId);
            default -> throw new IllegalArgumentException("Unsupported conversation type: " + conversationType);
        }

        ChatMessage msg = chatService.sendMessage(senderId, receiverId, memoId, branchId, conversationType, message);
        return ResponseEntity.ok(ApiResponse.success("Message sent", msg));
    }

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getMemoMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long memoId) {
        chatAuth.assertCanAccessMemo(userId, memoId);
        return ResponseEntity.ok(ApiResponse.success(chatService.getMemoMessages(memoId)));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getBranchMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long branchId) {
        chatAuth.assertCanAccessBranch(userId, branchId);
        return ResponseEntity.ok(ApiResponse.success(chatService.getBranchMessages(branchId)));
    }

    @GetMapping("/branch/{branchId}/paginated")
    public ResponseEntity<ApiResponse<Page<ChatMessage>>> getBranchMessagesPaginated(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        chatAuth.assertCanAccessBranch(userId, branchId);
        PageRequest pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(ApiResponse.success(chatService.getBranchMessagesPaginated(branchId, pr)));
    }

    @GetMapping("/conversation/{otherId}")
    public ResponseEntity<ApiResponse<Page<ChatMessage>>> getConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long otherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        chatAuth.assertCanDirectMessage(userId, otherId);
        PageRequest pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(ApiResponse.success(chatService.getConversation(userId, otherId, pr)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(@AuthenticationPrincipal Long userId) {
        Map<String, Long> counts = new HashMap<>();
        counts.put("direct", chatService.getUnreadCount(userId));
        counts.put("branch", chatService.getUnreadBranchMessagesCount());
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    /**
     * Participant directory for chat display names. Returns only id + name for ACTIVE users
     * (never email, username or any account/security field). Available to any authenticated
     * user because participant names are required to render conversations.
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<ChatUserDto>>> getChatUsers() {
        List<ChatUserDto> users = userRepository.findByStatus(Status.ACTIVE).stream()
                .map(u -> new ChatUserDto(u.getId(), u.getName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @GetMapping("/admin/all-branch-messages")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ChatMessage>>> getAllBranchMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(ApiResponse.success(chatService.getAllBranchMessages(pr)));
    }

    @GetMapping("/admin/active-branches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Long>>> getActiveBranchIds() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getActiveBranchIds()));
    }

    /**
     * Admin-only full user enumeration (kept for admin tooling). Returns the same slim DTO —
     * the legacy full-entity response was leaking PII to every authenticated caller.
     */
    @GetMapping("/admin/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ChatUserDto>>> getAllUsers() {
        List<ChatUserDto> users = userRepository.findAll().stream()
                .map(u -> new ChatUserDto(u.getId(), u.getName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    @PutMapping("/branch/{branchId}/mark-read")
    public ResponseEntity<ApiResponse<Void>> markBranchRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long branchId) {
        chatAuth.assertCanAccessBranch(userId, branchId);
        chatService.markBranchMessagesAsRead(branchId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        chatService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    /**
     * Backwards-compatible type resolution. Explicit conversationType wins; otherwise infer
     * from the supplied resource so legacy callers that only send branchId/memoId keep working.
     */
    private static String resolveConversationType(Object rawType, Long branchId, Long memoId, Long receiverId) {
        String type = rawType != null ? rawType.toString() : null;
        if (type != null && !type.isBlank()) {
            return type;
        }
        if (branchId != null) {
            return "BRANCH_CHAT";
        }
        if (memoId != null) {
            return "MEMO";
        }
        if (receiverId != null) {
            return "DIRECT_MESSAGE";
        }
        throw new IllegalArgumentException("Unable to determine conversation type");
    }
}
