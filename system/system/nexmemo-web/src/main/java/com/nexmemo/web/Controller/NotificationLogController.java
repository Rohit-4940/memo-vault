package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.NotificationLog;
import com.nexmemo.web.service.NotificationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification-logs")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationLogController {

    private final NotificationLogService notificationLogService;

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<NotificationLog>>> getByMemo(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success(notificationLogService.getNotificationsForMemo(memoId)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<NotificationLog>>> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(notificationLogService.getNotificationsForUser(userId)));
    }
}
