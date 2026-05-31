package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.web.service.MemoAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class DashboardController {

    private final MemoAnalyticsService analyticsService;

    @GetMapping("/dashboard-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDashboardSummary()));
    }

    @GetMapping("/expense-by-category")
    public ResponseEntity<ApiResponse<?>> getExpenseByCategory() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getExpenseByCategory()));
    }

    @GetMapping("/status-breakdown")
    public ResponseEntity<ApiResponse<?>> getStatusBreakdown() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getStatusBreakdown()));
    }

    @GetMapping("/monthly-trend")
    public ResponseEntity<ApiResponse<?>> getMonthlyTrend() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getMonthlyTrend()));
    }

    @GetMapping("/user-stats/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserStats(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getUserStats(userId)));
    }
}
