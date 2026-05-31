package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.web.service.ChatService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatMessage>> sendMessage(
            @AuthenticationPrincipal Long senderId,
            @RequestBody Map<String, Object> body) {
        Long receiverId = body.get("receiverId") != null ? ((Number) body.get("receiverId")).longValue() : null;
        Long memoId = body.get("memoId") != null ? ((Number) body.get("memoId")).longValue() : null;
        Long branchId = body.get("branchId") != null ? ((Number) body.get("branchId")).longValue() : null;
        String conversationType = (String) body.get("conversationType");
        String message = (String) body.get("message");
        ChatMessage msg = chatService.sendMessage(senderId, receiverId, memoId, branchId, conversationType, message);
        return ResponseEntity.ok(ApiResponse.success("Message sent", msg));
    }

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getMemoMessages(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getMemoMessages(memoId)));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<ChatMessage>>> getBranchMessages(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(chatService.getBranchMessages(branchId)));
    }

    @GetMapping("/branch/{branchId}/paginated")
    public ResponseEntity<ApiResponse<Page<ChatMessage>>> getBranchMessagesPaginated(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(ApiResponse.success(chatService.getBranchMessagesPaginated(branchId, pr)));
    }

    @GetMapping("/conversation/{otherId}")
    public ResponseEntity<ApiResponse<Page<ChatMessage>>> getConversation(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long otherId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(ApiResponse.success(chatService.getConversation(userId, otherId, pr)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(@AuthenticationPrincipal Long userId) {
        Map<String, Long> counts = new HashMap<>();
        counts.put("direct", chatService.getUnreadCount(userId));
        counts.put("branch", chatService.getUnreadBranchMessagesCount());
        return ResponseEntity.ok(ApiResponse.success(counts));
    }

    @GetMapping("/admin/all-branch-messages")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ChatMessage>>> getAllBranchMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdDate"));
        return ResponseEntity.ok(ApiResponse.success(chatService.getAllBranchMessages(pr)));
    }

    @GetMapping("/admin/active-branches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Long>>> getActiveBranchIds() {
        return ResponseEntity.ok(ApiResponse.success(chatService.getActiveBranchIds()));
    }

    @GetMapping("/admin/users")
    public ResponseEntity<ApiResponse<List<User>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userRepository.findAll()));
    }

    @PutMapping("/branch/{branchId}/mark-read")
    public ResponseEntity<ApiResponse<Void>> markBranchRead(@PathVariable Long branchId) {
        chatService.markBranchMessagesAsRead(branchId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        chatService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }
}
