package com.nexmemo.web.service;

import com.nexmemo.core.entity.ExpenseCategory;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.repository.MemoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemoAnalyticsService {

    private final MemoRepository memoRepository;

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
                        .divide(BigDecimal.valueOf(totalMemos), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        return summary;
    }

    public List<Map<String, Object>> getExpenseByCategory() {
        List<Object[]> results = memoRepository.countByExpenseCategory();
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            ExpenseCategory cat = (ExpenseCategory) row[0];
            long count = (Long) row[1];
            item.put("categoryId", cat.getId());
            item.put("categoryName", cat.getCategoryName());
            item.put("categoryCode", cat.getCategoryCode());
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
            long count = (Long) row[1];
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
            int month = (Integer) row[0];
            long count = (Long) row[1];
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
        long created = memoRepository.countByIsDeletedFalse();
        long pending = memoRepository.countByCurrentApproverIdAndIsDeletedFalse(userId);
        stats.put("memosCreated", created);
        stats.put("pendingApprovals", pending);
        return stats;
    }
}
