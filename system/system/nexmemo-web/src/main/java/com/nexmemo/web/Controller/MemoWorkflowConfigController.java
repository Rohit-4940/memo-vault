package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoWorkflowConfig;
import com.nexmemo.core.entity.MemoWorkflowPermission;
import com.nexmemo.core.entity.MemoWorkflowStage;
import com.nexmemo.web.service.MemoWorkflowConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memo-workflows")
@RequiredArgsConstructor
public class MemoWorkflowConfigController {

    private final MemoWorkflowConfigService workflowConfigService;

    // ── Config endpoints ──────────────────────────────────────────────────

    @GetMapping("/memo-type/{memoTypeId}/config")
    public ResponseEntity<ApiResponse<MemoWorkflowConfig>> getConfig(@PathVariable Long memoTypeId) {
        MemoWorkflowConfig config = workflowConfigService.getConfigByMemoType(memoTypeId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/memo-type/{memoTypeId}/config")
    public ResponseEntity<ApiResponse<MemoWorkflowConfig>> saveConfig(
            @PathVariable Long memoTypeId,
            @RequestBody MemoWorkflowConfig dto) {
        MemoWorkflowConfig saved = workflowConfigService.saveConfig(memoTypeId, dto);
        return ResponseEntity.ok(ApiResponse.success("Workflow config saved", saved));
    }

    @DeleteMapping("/memo-type/{memoTypeId}/config")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable Long memoTypeId) {
        workflowConfigService.deleteConfig(memoTypeId);
        return ResponseEntity.ok(ApiResponse.success("Workflow config deleted", null));
    }

    // ── Stage endpoints ───────────────────────────────────────────────────

    @GetMapping("/config/{configId}/stages")
    public ResponseEntity<ApiResponse<List<MemoWorkflowStage>>> getStages(@PathVariable Long configId) {
        return ResponseEntity.ok(ApiResponse.success(workflowConfigService.getStages(configId)));
    }

    @PutMapping("/config/{configId}/stages")
    public ResponseEntity<ApiResponse<List<MemoWorkflowStage>>> saveStages(
            @PathVariable Long configId,
            @RequestBody List<MemoWorkflowStage> stages) {
        List<MemoWorkflowStage> saved = workflowConfigService.saveStages(configId, stages);
        return ResponseEntity.ok(ApiResponse.success("Stages saved", saved));
    }

    // ── Permission endpoints ──────────────────────────────────────────────

    @GetMapping("/memo-type/{memoTypeId}/permissions")
    public ResponseEntity<ApiResponse<List<MemoWorkflowPermission>>> getPermissions(@PathVariable Long memoTypeId) {
        return ResponseEntity.ok(ApiResponse.success(workflowConfigService.getPermissions(memoTypeId)));
    }

    @PutMapping("/memo-type/{memoTypeId}/permissions")
    public ResponseEntity<ApiResponse<List<MemoWorkflowPermission>>> savePermissions(
            @PathVariable Long memoTypeId,
            @RequestBody List<MemoWorkflowPermission> permissions) {
        List<MemoWorkflowPermission> saved = workflowConfigService.savePermissions(memoTypeId, permissions);
        return ResponseEntity.ok(ApiResponse.success("Permissions saved", saved));
    }
}
