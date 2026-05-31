package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.entity.ApprovalHistory;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoCc;
import com.nexmemo.core.entity.MemoDocument;
import com.nexmemo.core.entity.MemoRouting;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.web.service.MemoService;
import com.nexmemo.web.service.WorkflowEngineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memos")
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;
    private final WorkflowEngineService workflowEngineService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Memo>>> getAllMemos() {
        return ResponseEntity.ok(ApiResponse.success(memoService.getAllMemos()));
    }

    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<Memo>> getAllMemosPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate") String sort,
            @RequestParam(defaultValue = "desc") String dir) {
        Sort s = dir.equalsIgnoreCase("asc") ? Sort.by(sort).ascending() : Sort.by(sort).descending();
        Pageable pageable = PageRequest.of(page, size, s);
        return ResponseEntity.ok(PageResponse.from(memoService.getAllMemosPaginated(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Memo>> getMemo(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getMemoById(id)));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyMemos(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        return ResponseEntity.ok(PageResponse.from(memoService.getMemosByCreatorPaginated(userId, pageable)));
    }

    @GetMapping("/pending-approvals")
    public ResponseEntity<ApiResponse<List<Memo>>> getPendingApprovals(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getPendingApprovals(userId)));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<ApiResponse<List<Memo>>> getByStatus(@PathVariable MemoStatus status) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getMemosByStatus(status)));
    }

    @GetMapping("/by-branch/{branchId}")
    public ResponseEntity<ApiResponse<List<Memo>>> getByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getMemosByBranch(branchId)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Memo>>> searchMemos(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(memoService.searchMemos(q)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Memo>> createMemo(@Valid @RequestBody MemoService.CreateMemoRequest request,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(memoService.createMemo(request, userId)));
    }

    @PutMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<Memo>> submitMemo(@PathVariable Long id,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo submitted", memoService.submitMemo(id, userId)));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Memo>> approveMemo(@PathVariable Long id,
                                                         @RequestParam(required = false) String remarks,
                                                         @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo approved", memoService.approveMemo(id, userId, remarks)));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Memo>> rejectMemo(@PathVariable Long id,
                                                        @RequestParam(required = false) String remarks,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo rejected", memoService.rejectMemo(id, userId, remarks)));
    }

    @PutMapping("/{id}/return")
    public ResponseEntity<ApiResponse<Memo>> returnMemo(@PathVariable Long id,
                                                        @RequestParam(required = false) String remarks,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo returned for revision", memoService.returnMemo(id, userId, remarks)));
    }

    @PutMapping("/{id}/forward")
    public ResponseEntity<ApiResponse<Memo>> forwardMemo(@PathVariable Long id,
                                                         @RequestParam Long toUserId,
                                                         @RequestParam(required = false) String remarks,
                                                         @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo forwarded", memoService.forwardMemo(id, userId, toUserId, remarks)));
    }

    @PutMapping("/{id}/backward")
    public ResponseEntity<ApiResponse<Memo>> backwardMemo(@PathVariable Long id,
                                                          @RequestParam(required = false) String remarks,
                                                          @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo backwarded", memoService.backwardMemo(id, userId, remarks)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelMemo(@PathVariable Long id,
                                                        @AuthenticationPrincipal Long userId) {
        memoService.cancelMemo(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Memo cancelled", null));
    }

    @GetMapping("/{id}/routing")
    public ResponseEntity<ApiResponse<List<MemoRouting>>> getMemoRouting(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getMemoRoutingPath(id)));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<ApprovalHistory>>> getApprovalHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getApprovalHistory(id)));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<List<MemoDocument>>> getDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getDocuments(id)));
    }

    @GetMapping("/{id}/cc")
    public ResponseEntity<ApiResponse<List<MemoCc>>> getMemoCc(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getMemoCcList(id)));
    }

    @GetMapping("/my-cc")
    public ResponseEntity<ApiResponse<List<Memo>>> getMyCcMemos(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(memoService.getMyCcMemos(userId)));
    }

    @PutMapping("/{id}/acknowledge")
    public ResponseEntity<ApiResponse<MemoCc>> acknowledgeMemo(@PathVariable Long id,
                                                               @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Memo acknowledged", memoService.acknowledgeMemo(id, userId)));
    }

    @GetMapping("/{id}/stages")
    public ResponseEntity<ApiResponse<List<WorkflowStageInstance>>> getStageInstances(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(workflowEngineService.getStageInstances(id)));
    }

    @PostMapping("/{id}/approve-stage")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> approveStage(
            @PathVariable Long id, @RequestParam Long stageId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.approveStage(id, stageId, userId, comment)));
    }

    @PostMapping("/{id}/reject-stage")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> rejectStage(
            @PathVariable Long id, @RequestParam Long stageId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.rejectStage(id, stageId, userId, comment)));
    }

    @PostMapping("/{id}/return-stage")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> returnToStage(
            @PathVariable Long id, @RequestParam Long stageId,
            @RequestParam(required = false) String comment,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.returnToStage(id, stageId, userId, comment)));
    }

    @PostMapping("/{id}/delegate-stage")
    public ResponseEntity<ApiResponse<WorkflowStageInstance>> delegateStage(
            @PathVariable Long id, @RequestParam Long stageId,
            @RequestParam Long toUserId,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowEngineService.delegateStage(id, stageId, userId, toUserId)));
    }
}
