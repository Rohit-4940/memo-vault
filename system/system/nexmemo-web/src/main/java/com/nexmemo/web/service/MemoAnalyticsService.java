package com.nexmemo.web.service;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.Priority;
import com.nexmemo.core.repository.ApprovalHistoryRepository;
import com.nexmemo.core.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemoAnalyticsService {

    private final MemoRepository memoRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;

    /** SLA targets (hours) by priority — applicable to ALL Command Center logic */
    private static final Map<String, Integer> SLA_HOURS = Map.of(
            "URGENT", 4,
            "HIGH", 8,
            "MEDIUM", 24,
            "LOW", 72
    );

    // ── Original endpoints (unchanged) ────────────────────────────────────────

    public Map<String, Object> getDashboardSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        long totalMemos = memoRepository.countByIsDeletedFalse();
        long draftCount = memoRepository.countByStatusAndIsDeletedFalse(MemoStatus.DRAFT);
        long pendingCount = memoRepository.countByStatusAndIsDeletedFalse(MemoStatus.PENDING_APPROVAL);
        long approvedCount = memoRepository.countByStatusAndIsDeletedFalse(MemoStatus.APPROVED);
        long rejectedCount = memoRepository.countByStatusAndIsDeletedFalse(MemoStatus.REJECTED);

        BigDecimal totalPendingAmount = memoRepository.sumAmountByStatus(MemoStatus.PENDING_APPROVAL);
        BigDecimal totalApprovedAmount = memoRepository.sumAmountByStatus(MemoStatus.APPROVED);
        BigDecimal totalRejectedAmount = memoRepository.sumAmountByStatus(MemoStatus.REJECTED);

        summary.put("totalMemos", totalMemos);
        summary.put("draftCount", draftCount);
        summary.put("pendingCount", pendingCount);
        summary.put("approvedCount", approvedCount);
        summary.put("rejectedCount", rejectedCount);
        summary.put("totalPendingAmount", totalPendingAmount != null ? totalPendingAmount : BigDecimal.ZERO);
        summary.put("totalApprovedAmount", totalApprovedAmount != null ? totalApprovedAmount : BigDecimal.ZERO);
        summary.put("totalRejectedAmount", totalRejectedAmount != null ? totalRejectedAmount : BigDecimal.ZERO);
        summary.put("approvalRate", totalMemos > 0
                ? BigDecimal.valueOf(approvedCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalMemos), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return summary;
    }

    public List<Map<String, Object>> getExpenseByCategory() {
        List<Object[]> results = memoRepository.countByExpenseCategory();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            Object cat = row[0];
            long count = ((Number) row[1]).longValue();
            item.put("categoryId", cat instanceof com.nexmemo.core.entity.ExpenseCategory ? ((com.nexmemo.core.entity.ExpenseCategory) cat).getId() : 0);
            item.put("categoryName", cat instanceof com.nexmemo.core.entity.ExpenseCategory ? ((com.nexmemo.core.entity.ExpenseCategory) cat).getCategoryName() : "Unknown");
            item.put("categoryCode", cat instanceof com.nexmemo.core.entity.ExpenseCategory ? ((com.nexmemo.core.entity.ExpenseCategory) cat).getCategoryCode() : "");
            item.put("memoCount", count);
            categories.add(item);
        }
        return categories;
    }

    public List<Map<String, Object>> getStatusBreakdown() {
        List<Object[]> results = memoRepository.countByStatus();
        List<Map<String, Object>> breakdown = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            MemoStatus status = (MemoStatus) row[0];
            long count = ((Number) row[1]).longValue();
            item.put("status", status.name());
            item.put("label", status.toString());
            item.put("count", count);
            breakdown.add(item);
        }
        return breakdown;
    }

    public List<Map<String, Object>> getMonthlyTrend() {
        int year = LocalDate.now().getYear();
        List<Object[]> results = memoRepository.countByMonth(year);
        Map<Integer, Long> monthMap = new HashMap<>();
        for (Object[] row : results) {
            int month = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            monthMap.put(month, count);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            Map<String, Object> item = new LinkedHashMap<>();
            YearMonth ym = YearMonth.of(year, m);
            item.put("month", ym.toString());
            item.put("label", ym.getMonth().toString().substring(0, 3));
            item.put("count", monthMap.getOrDefault(m, 0L));
            trend.add(item);
        }
        return trend;
    }

    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        long created = memoRepository.countByCreatedBy(userId);
        long pending = memoRepository.countByCurrentApproverIdAndIsDeletedFalse(userId);
        stats.put("memosCreated", created);
        stats.put("pendingApprovals", pending);
        return stats;
    }

    // ── Command Center ─────────────────────────────────────────────────────

    public Map<String, Object> getCommandCenter() {
        Map<String, Object> cc = new LinkedHashMap<>();
        // Isolate failures — each section fails independently
        putSafely(cc, "summary", this::getDashboardSummary);
        putSafely(cc, "slaCompliance", this::getSlaCompliance);
        putSafely(cc, "bottleneckHeatmap", this::getBottleneckHeatmap);
        putSafely(cc, "approverLeaderboard", this::getApproverLeaderboard);
        putSafely(cc, "departmentComparison", this::getDepartmentComparison);
        putSafely(cc, "trendForecast", this::getTrendForecast);
        putSafely(cc, "slaAtRisk", this::getSlaAtRisk);
        putSafely(cc, "activityFeed", this::getActivityFeed);
        return cc;
    }

    private void putSafely(Map<String, Object> target, String key, java.util.function.Supplier<?> supplier) {
        try {
            target.put(key, supplier.get());
        } catch (Exception e) {
            log.warn("Command Center section '{}' failed: {}", key, e.getMessage());
            target.put(key, e instanceof java.util.EmptyStackException ? List.of() : Map.of("error", e.getMessage()));
        }
    }

    // ── SLA Compliance ────────────────────────────────────────────────────
    public Map<String, Object> getSlaCompliance() {
        List<Object[]> rows = memoRepository.slaComplianceByPriority();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        long grandTotal = 0, grandWithin = 0;
        for (Object[] row : rows) {
            com.nexmemo.core.enums.Priority prio = (com.nexmemo.core.enums.Priority) row[0];
            String priority = prio.name();
            long total = ((Number) row[1]).longValue();
            long withinSla = ((Number) row[2]).longValue();
            grandTotal += total;
            grandWithin += withinSla;
            BigDecimal pct = total > 0
                    ? BigDecimal.valueOf(withinSla).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("priority", priority);
            item.put("total", total);
            item.put("withinSla", withinSla);
            item.put("compliancePct", pct);
            item.put("slaHours", SLA_HOURS.getOrDefault(priority, 24));
            items.add(item);
        }
        BigDecimal overall = grandTotal > 0
                ? BigDecimal.valueOf(grandWithin).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(grandTotal), 1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);
        result.put("overallCompliancePct", overall);
        result.put("details", items);
        return result;
    }

    // ── Bottleneck Heatmap ────────────────────────────────────────────────
    public List<Map<String, Object>> getBottleneckHeatmap() {
        List<Object[]> rows = memoRepository.approvedMemosWithDeptAndType();
        // Group by department + memoType and compute avg hours in Java
        Map<String, Map<String, java.util.concurrent.atomic.DoubleAdder>> sums = new LinkedHashMap<>();
        Map<String, Map<String, java.util.concurrent.atomic.LongAdder>> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String dept = (String) row[0];
            String type = (String) row[1];
            java.time.LocalDateTime submitted = (java.time.LocalDateTime) row[2];
            java.time.LocalDateTime approved = (java.time.LocalDateTime) row[3];
            double hours = java.time.Duration.between(submitted, approved).toMinutes() / 60.0;
            sums.computeIfAbsent(dept, k -> new LinkedHashMap<>())
                .computeIfAbsent(type, k -> new java.util.concurrent.atomic.DoubleAdder()).add(hours);
            counts.computeIfAbsent(dept, k -> new LinkedHashMap<>())
                .computeIfAbsent(type, k -> new java.util.concurrent.atomic.LongAdder()).increment();
        }
        List<Map<String, Object>> heatmap = new ArrayList<>();
        for (var deptEntry : sums.entrySet()) {
            String dept = deptEntry.getKey();
            for (var typeEntry : deptEntry.getValue().entrySet()) {
                String type = typeEntry.getKey();
                double totalHours = typeEntry.getValue().sum();
                long count = counts.get(dept).get(type).sum();
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("department", dept);
                cell.put("memoType", type);
                cell.put("avgHours", count > 0 ? Math.round(totalHours / count * 10.0) / 10.0 : 0);
                cell.put("count", count);
                heatmap.add(cell);
            }
        }
        return heatmap;
    }

    // ── Approver Leaderboard ──────────────────────────────────────────────
    public List<Map<String, Object>> getApproverLeaderboard() {
        List<Object[]> timings = approvalHistoryRepository.approverTimings();
        // Group by approver, compute count + avg time + current pending count
        Map<Long, List<Double>> approverHours = new LinkedHashMap<>();
        Map<Long, String> approverNames = new LinkedHashMap<>();
        for (Object[] row : timings) {
            Long id = ((Number) row[0]).longValue();
            String name = (String) row[1];
            approverNames.put(id, name);
            java.time.LocalDateTime actionDate = (java.time.LocalDateTime) row[2];
            java.time.LocalDateTime submitted = (java.time.LocalDateTime) row[3];
            double hours = java.time.Duration.between(submitted, actionDate).toMinutes() / 60.0;
            approverHours.computeIfAbsent(id, k -> new ArrayList<>()).add(hours);
        }
        // Get pending counts for each approver
        java.util.Map<Long, Long> pendingCounts = new java.util.HashMap<>();
        try {
            List<Memo> pendingMemos = memoRepository.findAllPendingWithDeadlines();
            for (Memo m : pendingMemos) {
                if (m.getCurrentApprover() != null) {
                    Long id = m.getCurrentApprover().getId();
                    pendingCounts.merge(id, 1L, Long::sum);
                }
            }
        } catch (Exception e) {
            log.warn("Could not load pending counts for leaderboard: {}", e.getMessage());
        }
        List<Map<String, Object>> board = new ArrayList<>();
        for (var entry : approverHours.entrySet()) {
            Long id = entry.getKey();
            List<Double> hours = entry.getValue();
            double avg = hours.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            Map<String, Object> leader = new LinkedHashMap<>();
            leader.put("approverId", id);
            leader.put("approverName", approverNames.get(id));
            leader.put("totalApproved", hours.size());
            leader.put("avgApprovalHours", Math.round(avg * 10.0) / 10.0);
            leader.put("pendingCount", pendingCounts.getOrDefault(id, 0L));
            board.add(leader);
        }
        board.sort((a, b) -> Long.compare(((Number) b.get("totalApproved")).longValue(), ((Number) a.get("totalApproved")).longValue()));
        return board;
    }

    // ── Department Comparison ─────────────────────────────────────────────
    public List<Map<String, Object>> getDepartmentComparison() {
        List<Object[]> counts = memoRepository.departmentCounts();
        Map<String, long[]> deptMap = new LinkedHashMap<>();
        for (Object[] row : counts) {
            String name = (String) row[0];
            long total = ((Number) row[1]).longValue();
            long pending = ((Number) row[2]).longValue();
            long approved = ((Number) row[3]).longValue();
            deptMap.put(name, new long[]{total, pending, approved});
        }
        // Compute avg approval hours per department
        List<Object[]> timings = memoRepository.departmentApprovalTimes();
        Map<String, java.util.concurrent.atomic.DoubleAdder> hoursSum = new LinkedHashMap<>();
        Map<String, java.util.concurrent.atomic.LongAdder> timingCount = new LinkedHashMap<>();
        for (Object[] row : timings) {
            String name = (String) row[0];
            java.time.LocalDateTime submitted = (java.time.LocalDateTime) row[1];
            java.time.LocalDateTime approved = (java.time.LocalDateTime) row[2];
            if (submitted != null && approved != null) {
                double h = java.time.Duration.between(submitted, approved).toMinutes() / 60.0;
                hoursSum.computeIfAbsent(name, k -> new java.util.concurrent.atomic.DoubleAdder()).add(h);
                timingCount.computeIfAbsent(name, k -> new java.util.concurrent.atomic.LongAdder()).increment();
            }
        }
        List<Map<String, Object>> depts = new ArrayList<>();
        for (var entry : deptMap.entrySet()) {
            String name = entry.getKey();
            long[] vals = entry.getValue();
            double avgH = 0;
            if (hoursSum.containsKey(name)) {
                avgH = hoursSum.get(name).sum() / timingCount.get(name).sum();
            }
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("department", name);
            dept.put("total", vals[0]);
            dept.put("pending", vals[1]);
            dept.put("approved", vals[2]);
            dept.put("avgApprovalHours", Math.round(avgH * 10.0) / 10.0);
            depts.add(dept);
        }
        return depts;
    }

    // ── Trend Forecast ────────────────────────────────────────────────────
    public Map<String, Object> getTrendForecast() {
        LocalDate now = LocalDate.now();
        LocalDate since = now.minusMonths(18).withDayOfMonth(1);
        List<Object[]> rows = memoRepository.countByYearMonth(since.atStartOfDay());
        List<Map<String, Object>> history = new ArrayList<>();
        List<Double> recentValues = new ArrayList<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            history.add(Map.of("label", YearMonth.of(year, month).toString(), "count", count, "year", year, "month", month));
            if (YearMonth.of(year, month).isAfter(YearMonth.from(now).minusMonths(7))) {
                recentValues.add((double) count);
            }
        }
        List<Map<String, Object>> forecast = new ArrayList<>();
        if (recentValues.size() >= 3) {
            double avg = recentValues.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            for (int i = 1; i <= 3; i++) {
                YearMonth fm = YearMonth.from(now).plusMonths(i);
                forecast.add(Map.of("label", fm.toString(), "forecast", Math.round(avg),
                        "lowerBound", Math.max(0, Math.round(avg * 0.7)), "upperBound", Math.round(avg * 1.3)));
            }
        }
        return Map.of("history", history, "forecast", forecast);
    }

    // ── SLA At-Risk ───────────────────────────────────────────────────────
    public List<Map<String, Object>> getSlaAtRisk() {
        LocalDateTime now = LocalDateTime.now();
        List<Memo> memos = memoRepository.findAtRiskMemos(now);
        List<Map<String, Object>> atRisk = new ArrayList<>();
        for (Memo m : memos) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("memoId", m.getId());
            entry.put("memoNo", m.getMemoNo());
            entry.put("title", m.getTitle());
            entry.put("priority", m.getPriority().name());
            entry.put("department", m.getDepartment() != null ? m.getDepartment().getName() : "");
            entry.put("slaDeadline", m.getSlaDeadline() != null ? m.getSlaDeadline().toString() : "");
            if (m.getSlaDeadline() != null) {
                long elapsed = ChronoUnit.MINUTES.between(m.getCreatedDate(), now);
                long total = ChronoUnit.MINUTES.between(m.getCreatedDate(), m.getSlaDeadline());
                long remaining = total - elapsed;
                entry.put("elapsedMinutes", elapsed);
                entry.put("remainingMinutes", Math.max(0, remaining));
                entry.put("isOverdue", remaining < 0);
                entry.put("overdueMinutes", remaining < 0 ? Math.abs(remaining) : 0);
            }
            entry.put("amount", m.getAmount() != null ? m.getAmount() : BigDecimal.ZERO);
            atRisk.add(entry);
        }
        return atRisk;
    }

    // ── Activity Feed ─────────────────────────────────────────────────────
    public List<Map<String, Object>> getActivityFeed() {
        List<Object[]> rows = approvalHistoryRepository.recentActivity();
        List<Map<String, Object>> feed = new ArrayList<>();
        int limit = Math.min(rows.size(), 20);
        for (int i = 0; i < limit; i++) {
            Object[] row = rows.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row[0]);
            entry.put("action", row[1] instanceof Enum ? ((Enum<?>) row[1]).name() : String.valueOf(row[1]));
            entry.put("timestamp", row[2] != null ? row[2].toString() : "");
            entry.put("remarks", row[3] != null ? row[3] : "");
            entry.put("memoNo", row[4]);
            entry.put("memoTitle", row[5]);
            entry.put("approverName", row[6]);
            feed.add(entry);
        }
        return feed;
    }
}
