package com.nexmemo.web.service;

import com.nexmemo.core.entity.MemoWorkflowConfig;
import com.nexmemo.core.entity.MemoWorkflowPermission;
import com.nexmemo.core.entity.MemoWorkflowStage;
import com.nexmemo.core.repository.MemoWorkflowConfigRepository;
import com.nexmemo.core.repository.MemoWorkflowPermissionRepository;
import com.nexmemo.core.repository.MemoWorkflowStageRepository;
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
public class MemoWorkflowConfigService {

    private final MemoWorkflowConfigRepository configRepository;
    private final MemoWorkflowStageRepository stageRepository;
    private final MemoWorkflowPermissionRepository permissionRepository;

    // ── Config ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MemoWorkflowConfig getConfigByMemoType(Long memoTypeId) {
        return configRepository.findByMemoTypeId(memoTypeId).orElse(null);
    }

    public MemoWorkflowConfig saveConfig(Long memoTypeId, MemoWorkflowConfig dto) {
        MemoWorkflowConfig config = configRepository.findByMemoTypeId(memoTypeId).orElse(new MemoWorkflowConfig());
        config.setMemoTypeId(memoTypeId);
        config.setAssignmentType(dto.getAssignmentType());
        config.setFlowType(dto.getFlowType());
        config.setSlaHours(dto.getSlaHours());
        config.setRequireDocumentVerification(dto.getRequireDocumentVerification());
        config.setEnableVersioning(dto.getEnableVersioning());
        config.setRequiredFields(dto.getRequiredFields());
        config.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        config.setRevision(prevRevision(config.getId()) + 1);
        config = configRepository.save(config);
        log.info("Workflow config saved for memoTypeId={}, configId={} (revision {})",
                memoTypeId, config.getId(), config.getRevision());
        return config;
    }

    public void deleteConfig(Long memoTypeId) {
        configRepository.findByMemoTypeId(memoTypeId).ifPresent(config -> {
            stageRepository.deleteByConfigId(config.getId());
            configRepository.delete(config);
            log.info("Workflow config deleted for memoTypeId={}", memoTypeId);
        });
    }

    // ── Stages ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemoWorkflowStage> getStages(Long configId) {
        return stageRepository.findByConfigIdOrderByStageOrderAsc(configId);
    }

    public List<MemoWorkflowStage> saveStages(Long configId, List<MemoWorkflowStage> stages) {
        MemoWorkflowConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new EntityNotFoundException("Workflow config not found: " + configId));
        config.setRevision(prevRevision(config.getId()) + 1);
        configRepository.save(config);

        stageRepository.deleteByConfigId(configId);
        for (int i = 0; i < stages.size(); i++) {
            MemoWorkflowStage stage = stages.get(i);
            stage.setId(null);
            stage.setConfigId(configId);
            stage.setStageOrder(i + 1);
            stageRepository.save(stage);
        }
        log.info("{} stages saved for configId={} (revision {})", stages.size(), configId, config.getRevision());
        return stageRepository.findByConfigIdOrderByStageOrderAsc(configId);
    }

    private int prevRevision(Long configId) {
        if (configId == null) {
            return 0;
        }
        return configRepository.findById(configId)
                .map(MemoWorkflowConfig::getRevision)
                .map(r -> r == null ? 0 : r)
                .orElse(0);
    }

    // ── Permissions ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MemoWorkflowPermission> getPermissions(Long memoTypeId) {
        return permissionRepository.findByMemoTypeId(memoTypeId);
    }

    @Transactional(readOnly = true)
    public MemoWorkflowPermission getPermissionByMemoTypeAndRole(Long memoTypeId, Long roleId) {
        return permissionRepository.findByMemoTypeId(memoTypeId).stream()
                .filter(p -> p.getRoleId().equals(roleId))
                .findFirst().orElse(null);
    }

    public List<MemoWorkflowPermission> savePermissions(Long memoTypeId, List<MemoWorkflowPermission> permissions) {
        permissionRepository.deleteByMemoTypeId(memoTypeId);
        for (MemoWorkflowPermission perm : permissions) {
            perm.setId(null);
            perm.setMemoTypeId(memoTypeId);
            permissionRepository.save(perm);
        }
        log.info("{} permissions saved for memoTypeId={}", permissions.size(), memoTypeId);
        return permissionRepository.findByMemoTypeId(memoTypeId);
    }
}
