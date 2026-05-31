package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.ApprovalRuleMatrix;
import com.nexmemo.core.repository.ApprovalRuleMatrixRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/approval-rule-matrices")
@RequiredArgsConstructor
public class ApprovalRuleMatrixController {

    private final ApprovalRuleMatrixRepository approvalRuleMatrixRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApprovalRuleMatrix>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(approvalRuleMatrixRepository.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalRuleMatrix>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(approvalRuleMatrixRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApprovalRuleMatrix not found: " + id))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApprovalRuleMatrix>> create(@Valid @RequestBody ApprovalRuleMatrix matrix) {
        return ResponseEntity.ok(ApiResponse.created(approvalRuleMatrixRepository.save(matrix)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalRuleMatrix>> update(@PathVariable Long id, @Valid @RequestBody ApprovalRuleMatrix updated) {
        ApprovalRuleMatrix matrix = approvalRuleMatrixRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ApprovalRuleMatrix not found: " + id));
        matrix.setCategory(updated.getCategory());
        matrix.setStage(updated.getStage());
        matrix.setRoleId(updated.getRoleId());
        matrix.setUserId(updated.getUserId());
        matrix.setPriority(updated.getPriority());
        matrix.setCanApprove(updated.getCanApprove());
        matrix.setCanReject(updated.getCanReject());
        matrix.setCanReturn(updated.getCanReturn());
        matrix.setCanForward(updated.getCanForward());
        matrix.setCanDelegate(updated.getCanDelegate());
        return ResponseEntity.ok(ApiResponse.success(approvalRuleMatrixRepository.save(matrix)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        approvalRuleMatrixRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Matrix entry deleted", null));
    }

    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ApprovalRuleMatrix>>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(approvalRuleMatrixRepository.findByCategoryIdOrderByPriorityAsc(categoryId)));
    }
}
