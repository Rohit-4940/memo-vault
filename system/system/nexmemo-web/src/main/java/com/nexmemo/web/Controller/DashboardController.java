package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.web.service.MemoAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final MemoAnalyticsService analyticsService;

    // ── Original endpoints ─────────────────────────────────────────────────

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

    // ── Command Center endpoints ────────────────────────────────────────────

    @GetMapping("/command-center")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommandCenter() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getCommandCenter()));
    }

    @GetMapping("/sla-compliance")
    public ResponseEntity<ApiResponse<?>> getSlaCompliance() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getSlaCompliance()));
    }

    @GetMapping("/bottleneck-heatmap")
    public ResponseEntity<ApiResponse<?>> getBottleneckHeatmap() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getBottleneckHeatmap()));
    }

    @GetMapping("/approver-leaderboard")
    public ResponseEntity<ApiResponse<?>> getApproverLeaderboard() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getApproverLeaderboard()));
    }

    @GetMapping("/department-comparison")
    public ResponseEntity<ApiResponse<?>> getDepartmentComparison() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getDepartmentComparison()));
    }

    @GetMapping("/trend-forecast")
    public ResponseEntity<ApiResponse<?>> getTrendForecast() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getTrendForecast()));
    }

    @GetMapping("/sla-at-risk")
    public ResponseEntity<ApiResponse<?>> getSlaAtRisk() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getSlaAtRisk()));
    }

    @GetMapping("/activity-feed")
    public ResponseEntity<ApiResponse<?>> getActivityFeed() {
        return ResponseEntity.ok(ApiResponse.success(analyticsService.getActivityFeed()));
    }
}
