package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.entity.Notification;
import com.nexmemo.web.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PageResponse<Notification>> getNotifications(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        return ResponseEntity.ok(PageResponse.from(notificationService.getUserNotifications(userId, pageable)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount(@AuthenticationPrincipal Long userId) {
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead(@AuthenticationPrincipal Long userId) {
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        int updated = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }
}
