package com.nexmemo.web.service;

import com.nexmemo.core.entity.Department;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.LeaveStatus;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.RoleType;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.LeaveTrackerRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngineService {

    private final UserRepository userRepository;
    private final LeaveTrackerRepository leaveTrackerRepository;

    public int determineApprovalLevels(Memo memo) {
        BigDecimal amount = memo.getAmount() != null ? memo.getAmount() : BigDecimal.ZERO;
        Department dept = memo.getDepartment();
        return calculateRequiredLevels(amount, dept);
    }

    public User getNextApprover(Memo memo, int currentLevel) {
        int nextLevel = currentLevel + 1;
        BigDecimal amount = memo.getAmount() != null ? memo.getAmount() : BigDecimal.ZERO;

        List<User> potentialApprovers = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == Status.ACTIVE)
                .filter(u -> u.getRole() != null)
                .filter(u -> u.getRole().getRoleType() == RoleType.APPROVAL
                        || u.getRole().getRoleType() == RoleType.COMMITTEE
                        || u.getRole().getRoleType() == RoleType.ADMIN
                        || u.getRole().getRoleType() == RoleType.SUPER_ADMIN)
                .filter(u -> Boolean.TRUE.equals(u.getRole().getIsRoleApprovable()))
                .filter(u -> u.getRole().getRoleApprovalLimitAmount() == null
                        || u.getRole().getRoleApprovalLimitAmount().compareTo(amount) >= 0)
                .filter(u -> !isUserOnLeave(u.getId()))
                .collect(Collectors.toList());

        if (potentialApprovers.isEmpty()) {
            potentialApprovers = userRepository.findAll().stream()
                    .filter(u -> u.getStatus() == Status.ACTIVE)
                    .filter(u -> u.getRole() != null)
                    .filter(u -> u.getRole().getRoleType() == RoleType.ADMIN
                            || u.getRole().getRoleType() == RoleType.SUPER_ADMIN)
                    .filter(u -> !isUserOnLeave(u.getId()))
                    .collect(Collectors.toList());
        }

        if (potentialApprovers.isEmpty()) {
            throw new RuntimeException("No available approver found for memo " + memo.getMemoNo());
        }

        if (nextLevel == 1) {
            return findLevel1Approver(potentialApprovers);
        } else if (nextLevel == 2) {
            return findLevel2Approver(potentialApprovers);
        } else {
            return findLevel3Approver(potentialApprovers);
        }
    }

    public int calculateRequiredLevels(BigDecimal amount, Department dept) {
        if (amount == null) amount = BigDecimal.ZERO;

        if (amount.compareTo(new BigDecimal("10000000")) > 0) {
            return 3;
        } else if (amount.compareTo(new BigDecimal("1000000")) > 0) {
            return 2;
        } else {
            return 1;
        }
    }

    public boolean isApprovalComplete(Memo memo) {
        return memo.getApprovalLevel() >= memo.getTotalApprovalLevels()
                && memo.getStatus() == MemoStatus.APPROVED;
    }

    public boolean hasAuthorityToApprove(User approver, Memo memo) {
        if (approver == null || approver.getRole() == null) return false;
        if (approver.getStatus() != Status.ACTIVE) return false;

        Role role = approver.getRole();
        if (!Boolean.TRUE.equals(role.getIsRoleApprovable())) return false;

        if (role.getRoleType() == RoleType.ADMIN || role.getRoleType() == RoleType.SUPER_ADMIN) {
            return true;
        }

        BigDecimal amount = memo.getAmount() != null ? memo.getAmount() : BigDecimal.ZERO;
        if (role.getRoleApprovalLimitAmount() != null
                && role.getRoleApprovalLimitAmount().compareTo(amount) >= 0) {
            return true;
        }

        return false;
    }

    private User findLevel1Approver(List<User> approvers) {
        return approvers.stream()
                .filter(u -> u.getRole().getRoleType() == RoleType.APPROVAL)
                .findFirst()
                .orElseGet(() -> approvers.stream()
                        .filter(u -> u.getRole().getRoleType() == RoleType.COMMITTEE)
                        .findFirst()
                        .orElse(approvers.get(0)));
    }

    private User findLevel2Approver(List<User> approvers) {
        return approvers.stream()
                .filter(u -> u.getRole().getRoleType() == RoleType.COMMITTEE)
                .findFirst()
                .orElseGet(() -> approvers.stream()
                        .filter(u -> u.getRole().getRoleType() == RoleType.APPROVAL)
                        .findFirst()
                        .orElse(approvers.get(0)));
    }

    private User findLevel3Approver(List<User> approvers) {
        return approvers.stream()
                .filter(u -> u.getRole().getRoleType() == RoleType.ADMIN
                        || u.getRole().getRoleType() == RoleType.SUPER_ADMIN)
                .findFirst()
                .orElseGet(() -> approvers.stream()
                        .filter(u -> u.getRole().getRoleType() == RoleType.COMMITTEE)
                        .findFirst()
                        .orElse(approvers.get(0)));
    }

    private boolean isUserOnLeave(Long userId) {
        return leaveTrackerRepository.existsByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                userId, LeaveStatus.APPROVED, LocalDate.now(), LocalDate.now());
    }
}
