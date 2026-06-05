package com.nexmemo.core.repository;

import com.nexmemo.core.entity.LeaveTracker;
import com.nexmemo.core.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveTrackerRepository extends JpaRepository<LeaveTracker, Long>, JpaSpecificationExecutor<LeaveTracker> {
    List<LeaveTracker> findByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(Long userId, LeaveStatus status, LocalDate endDate, LocalDate startDate);
    boolean existsByUserIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(Long userId, LeaveStatus status, LocalDate date1, LocalDate date2);
}
