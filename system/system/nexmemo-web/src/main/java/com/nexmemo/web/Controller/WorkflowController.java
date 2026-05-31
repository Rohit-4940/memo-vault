package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.entity.WorkflowStage;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.web.service.WorkflowEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngineService workflowEngineService;

    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<WorkflowStage>>> getAllStages() {
        return ResponseEntity.ok(ApiResponse.success(workflowEngineService.getAllStages()));
    }

    @PostMapping("/stages")
    public ResponseEntity<ApiResponse<WorkflowStage>> createStage(@Valid @RequestBody WorkflowStage stage) {
        return ResponseEntity.ok(ApiResponse.created(workflowEngineService.createStage(stage)));
    }

    @PutMapping("/stages/{id}")
    public ResponseEntity<ApiResponse<WorkflowStage>> updateStage(@PathVariable Long id, @Valid @RequestBody WorkflowStage stage) {
        return ResponseEntity.ok(ApiResponse.success(workflowEngineService.updateStage(id, stage)));
    }

    @DeleteMapping("/stages/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteStage(@PathVariable Long id) {
        workflowEngineService.deleteStage(id);
        return ResponseEntity.ok(ApiResponse.success("Stage deleted", null));
    }

    @PutMapping("/stages/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderStages(@RequestBody List<Long> stageIds) {
        workflowEngineService.reorderStages(stageIds);
        return ResponseEntity.ok(ApiResponse.success("Stages reordered", null));
    }

    @GetMapping("/instances/{memoId}")
    public ResponseEntity<ApiResponse<List<WorkflowStageInstance>>> getInstances(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success(workflowEngineService.getStageInstances(memoId)));
    }

    @PostMapping("/instances/{memoId}/stages/{stageId}/approve")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> approveStage(
            @PathVariable Long memoId, @PathVariable Long stageId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.approveStage(memoId, stageId, userId, comment)));
    }

    @PostMapping("/instances/{memoId}/stages/{stageId}/reject")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> rejectStage(
            @PathVariable Long memoId, @PathVariable Long stageId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.rejectStage(memoId, stageId, userId, comment)));
    }

    @PostMapping("/instances/{memoId}/stages/{stageId}/return")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> returnToStage(
            @PathVariable Long memoId, @PathVariable Long stageId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.returnToStage(memoId, stageId, userId, comment)));
    }

    @PostMapping("/instances/{memoId}/stages/{stageId}/delegate")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> delegateStage(
            @PathVariable Long memoId, @PathVariable Long stageId,
            @RequestParam Long toUserId,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.delegateStage(memoId, stageId, userId, toUserId)));
    }
}
