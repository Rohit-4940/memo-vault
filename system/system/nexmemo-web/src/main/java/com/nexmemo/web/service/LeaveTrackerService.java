package com.nexmemo.web.service;

import com.nexmemo.core.entity.LeaveTracker;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.LeaveStatus;
import com.nexmemo.core.repository.LeaveTrackerRepository;
import com.nexmemo.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LeaveTrackerService {

    private final LeaveTrackerRepository leaveTrackerRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<LeaveTracker> getAll() {
        return leaveTrackerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public LeaveTracker getById(Long id) {
        return leaveTrackerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LeaveTracker not found with id: " + id));
    }

    public LeaveTracker create(LeaveTracker leave) {
        Long userId = leave.getUser() != null ? leave.getUser().getId() : null;
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
            leave.setUser(user);
        }
        leave.setStatus(LeaveStatus.PENDING);
        LeaveTracker saved = leaveTrackerRepository.save(leave);
        log.info("Leave created for user {}: {} to {}", saved.getUser().getId(), saved.getStartDate(), saved.getEndDate());
        return saved;
    }

    public LeaveTracker update(Long id, LeaveTracker leave) {
        LeaveTracker existing = leaveTrackerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LeaveTracker not found with id: " + id));
        existing.setStartDate(leave.getStartDate());
        existing.setEndDate(leave.getEndDate());
        existing.setLeaveType(leave.getLeaveType());
        existing.setReason(leave.getReason());
        if (leave.getStatus() != null) existing.setStatus(leave.getStatus());
        existing = leaveTrackerRepository.save(existing);
        log.info("Leave updated: {}", existing.getId());
        return existing;
    }

    public void delete(Long id) {
        if (!leaveTrackerRepository.existsById(id)) {
            throw new EntityNotFoundException("LeaveTracker not found with id: " + id);
        }
        leaveTrackerRepository.deleteById(id);
        log.info("Leave deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public List<LeaveTracker> getByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        return leaveTrackerRepository.findAll().stream()
                .filter(l -> l.getUser() != null && l.getUser().getId().equals(userId))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LeaveTracker> getActiveLeaves(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        return leaveTrackerRepository.findByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                userId, LeaveStatus.APPROVED, LocalDate.now(), LocalDate.now());
    }

    public LeaveTracker approveLeave(Long id, Long approverId) {
        LeaveTracker leave = leaveTrackerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LeaveTracker not found with id: " + id));
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + approverId));
        leave.setStatus(LeaveStatus.APPROVED);
        leave.setApprovedBy(approver);
        leave = leaveTrackerRepository.save(leave);
        log.info("Leave {} approved by user {}", id, approverId);
        return leave;
    }

    public LeaveTracker rejectLeave(Long id, Long approverId, String reason) {
        LeaveTracker leave = leaveTrackerRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("LeaveTracker not found with id: " + id));
        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + approverId));
        leave.setStatus(LeaveStatus.REJECTED);
        leave.setApprovedBy(approver);
        leave.setRemarks(reason);
        leave = leaveTrackerRepository.save(leave);
        log.info("Leave {} rejected by user {}: {}", id, approverId, reason);
        return leave;
    }

    @Transactional(readOnly = true)
    public boolean isUserOnLeave(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with id: " + userId);
        }
        return leaveTrackerRepository.existsByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                userId, LeaveStatus.APPROVED, LocalDate.now(), LocalDate.now());
    }
}
