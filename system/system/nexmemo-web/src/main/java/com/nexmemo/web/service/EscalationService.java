package com.nexmemo.web.service;

import com.nexmemo.core.entity.EscalationRule;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.entity.WorkflowStage;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.EscalationAction;
import com.nexmemo.core.enums.WorkflowStageStatus;
import com.nexmemo.core.repository.EscalationRuleRepository;
import com.nexmemo.core.repository.WorkflowStageInstanceRepository;
import com.nexmemo.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EscalationService {

    private final EscalationRuleRepository escalationRuleRepository;
    private final WorkflowStageInstanceRepository stageInstanceRepository;
    private final UserRepository userRepository;

    public void escalate(Long stageInstanceId) {
        WorkflowStageInstance instance = stageInstanceRepository.findById(stageInstanceId)
                .orElseThrow(() -> new EntityNotFoundException("StageInstance not found: " + stageInstanceId));

        WorkflowStage stage = instance.getStage();
        if (stage == null) {
            // Phase 5 config-driven instances do not reference a global workflow_stage, so no
            // global escalation rule is attached. Nothing to escalate.
            log.warn("No escalation rule available for config-driven stage instance {} (stage '{}')",
                    stageInstanceId, instance.getStageName());
            return;
        }
        if (stage.getEscalationRule() == null) {
            log.warn("No escalation rule for stage {} on instance {}", stage.getId(), stageInstanceId);
            return;
        }

        EscalationRule rule = stage.getEscalationRule();
        switch (rule.getEscalationAction()) {
            case NOTIFY_SUPERVISOR:
            case REASSIGN_TO_SUPERVISOR:
                handleReassign(instance, rule);
                break;
            case ESCALATE_TO_HIGHER_LEVEL:
                handleHigherLevelEscalation(instance, rule);
                break;
            case AUTO_APPROVE:
                handleAutoApprove(instance);
                break;
            case AUTO_REJECT:
                handleAutoReject(instance);
                break;
            case NOTIFY_ALL_APPROVERS:
                notifyAllApprovers(instance, rule);
                break;
            default:
                log.warn("Unknown escalation action: {}", rule.getEscalationAction());
        }

        instance.setStatus(WorkflowStageStatus.ESCALATED);
        stageInstanceRepository.save(instance);
        log.info("Stage instance {} escalated via rule {}", stageInstanceId, rule.getName());
    }

    private void handleReassign(WorkflowStageInstance instance, EscalationRule rule) {
        if (rule.getEscalateToUser() != null) {
            User newAssignee = userRepository.findById(rule.getEscalateToUser())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + rule.getEscalateToUser()));
            instance.setAssignee(newAssignee);
            instance.setAssigneeIds(String.valueOf(newAssignee.getId()));
            log.info("Reassigned stage instance {} to user {}", instance.getId(), newAssignee.getId());
        }
    }

    private void handleHigherLevelEscalation(WorkflowStageInstance instance, EscalationRule rule) {
        if (rule.getEscalateToRole() != null) {
            List<User> users = userRepository.findByRoleId(rule.getEscalateToRole());
            if (!users.isEmpty()) {
                instance.setAssignee(users.get(0));
                instance.setAssigneeIds(String.valueOf(users.get(0).getId()));
                log.info("Escalated stage instance {} to role {} user {}", instance.getId(), rule.getEscalateToRole(), users.get(0).getId());
            }
        }
    }

    private void handleAutoApprove(WorkflowStageInstance instance) {
        instance.setStatus(WorkflowStageStatus.APPROVED);
        log.info("Auto-approved stage instance {}", instance.getId());
    }

    private void handleAutoReject(WorkflowStageInstance instance) {
        instance.setStatus(WorkflowStageStatus.REJECTED);
        log.info("Auto-rejected stage instance {}", instance.getId());
    }

    private void notifyAllApprovers(WorkflowStageInstance instance, EscalationRule rule) {
        log.info("SLA breach notification for stage instance {}", instance.getId());
    }

    @Transactional(readOnly = true)
    public List<EscalationRule> getAllRules() {
        return escalationRuleRepository.findAll();
    }

    public EscalationRule createRule(EscalationRule rule) {
        return escalationRuleRepository.save(rule);
    }

    public EscalationRule updateRule(Long id, EscalationRule updated) {
        EscalationRule rule = escalationRuleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("EscalationRule not found: " + id));
        rule.setName(updated.getName());
        rule.setTimeoutHours(updated.getTimeoutHours());
        rule.setEscalationAction(updated.getEscalationAction());
        rule.setEscalateToRole(updated.getEscalateToRole());
        rule.setEscalateToUser(updated.getEscalateToUser());
        return escalationRuleRepository.save(rule);
    }

    public void deleteRule(Long id) {
        escalationRuleRepository.deleteById(id);
    }
}
