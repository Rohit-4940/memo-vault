package com.nexmemo.web.service;

import com.nexmemo.core.entity.MemoWorkflowConfig;
import com.nexmemo.core.entity.MemoWorkflowStage;
import com.nexmemo.core.repository.MemoWorkflowConfigRepository;
import com.nexmemo.core.repository.MemoWorkflowPermissionRepository;
import com.nexmemo.core.repository.MemoWorkflowStageRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5 — workflow config revisioning: every config or stage save bumps the frozen revision so
 * in-flight instances can be told apart from later revisions.
 */
@ExtendWith(MockitoExtension.class)
class MemoWorkflowConfigServiceTest {

    @Mock private MemoWorkflowConfigRepository configRepository;
    @Mock private MemoWorkflowStageRepository stageRepository;
    @Mock private MemoWorkflowPermissionRepository permissionRepository;

    @InjectMocks private MemoWorkflowConfigService service;

    private MemoWorkflowConfig existingConfig(long id, int revision) {
        MemoWorkflowConfig c = new MemoWorkflowConfig();
        c.setId(id);
        c.setMemoTypeId(5L);
        c.setRevision(revision);
        return c;
    }

    @Test
    void saveConfig_existingConfig_bumpsRevision() {
        MemoWorkflowConfig existing = existingConfig(50L, 2);
        when(configRepository.findByMemoTypeId(5L)).thenReturn(Optional.of(existing));
        when(configRepository.findById(50L)).thenReturn(Optional.of(existing));
        when(configRepository.save(any(MemoWorkflowConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoWorkflowConfig dto = new MemoWorkflowConfig();
        dto.setAssignmentType("ROLE_BASED");
        dto.setFlowType("SEQUENTIAL");

        MemoWorkflowConfig saved = service.saveConfig(5L, dto);

        assertThat(saved.getRevision()).isEqualTo(3);
        assertThat(saved.getAssignmentType()).isEqualTo("ROLE_BASED");
        assertThat(saved.getMemoTypeId()).isEqualTo(5L);
    }

    @Test
    void saveConfig_newConfig_startsAtRevisionOne() {
        when(configRepository.findByMemoTypeId(5L)).thenReturn(Optional.empty());
        when(configRepository.save(any(MemoWorkflowConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoWorkflowConfig saved = service.saveConfig(5L, new MemoWorkflowConfig());

        assertThat(saved.getRevision()).isEqualTo(1);
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    void saveStages_bumpsRevisionAndOrdersStages() {
        MemoWorkflowConfig config = existingConfig(50L, 1);
        when(configRepository.findById(50L)).thenReturn(Optional.of(config));
        when(configRepository.save(any(MemoWorkflowConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoWorkflowStage a = new MemoWorkflowStage();
        a.setStageName("Verification");
        MemoWorkflowStage b = new MemoWorkflowStage();
        b.setStageName("Final Approval");
        when(stageRepository.findByConfigIdOrderByStageOrderAsc(50L)).thenReturn(List.of(a, b));

        List<MemoWorkflowStage> result = service.saveStages(50L, List.of(a, b));

        assertThat(config.getRevision()).isEqualTo(2);
        assertThat(result).hasSize(2);
        assertThat(a.getStageOrder()).isEqualTo(1);
        assertThat(b.getStageOrder()).isEqualTo(2);
        assertThat(a.getConfigId()).isEqualTo(50L);
        verify(stageRepository).deleteByConfigId(50L);
    }

    @Test
    void saveStages_unknownConfig_throws() {
        when(configRepository.findById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveStages(50L, List.of()))
                .isInstanceOf(EntityNotFoundException.class);
    }
}