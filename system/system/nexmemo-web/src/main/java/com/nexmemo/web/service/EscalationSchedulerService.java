package com.nexmemo.web.service;

import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.WorkflowStageStatus;
import com.nexmemo.core.repository.WorkflowStageInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscalationSchedulerService {

    private final WorkflowStageInstanceRepository stageInstanceRepository;
    private final EscalationService escalationService;

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void checkSlaBreaches() {
        log.debug("Checking for SLA breaches...");
        List<WorkflowStageInstance> overdue = stageInstanceRepository
                .findByStatusAndSlaDeadlineBefore(WorkflowStageStatus.IN_PROGRESS, LocalDateTime.now());

        for (WorkflowStageInstance instance : overdue) {
            log.info("SLA breach detected for stage instance {} (deadline: {})",
                    instance.getId(), instance.getSlaDeadline());
            try {
                escalationService.escalate(instance.getId());
            } catch (Exception e) {
                log.error("Failed to escalate stage instance {}: {}", instance.getId(), e.getMessage());
            }
        }

        if (!overdue.isEmpty()) {
            log.info("Processed {} SLA breaches", overdue.size());
        }
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupStaleInstances() {
        log.info("Running daily cleanup of stale stage instances...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(90);
        List<WorkflowStageInstance> stale = stageInstanceRepository
                .findByStatusAndSlaDeadlineBefore(WorkflowStageStatus.IN_PROGRESS, threshold);
        log.info("Found {} stale instances older than 90 days", stale.size());
    }
}
