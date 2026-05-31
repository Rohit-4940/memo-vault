package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.ApprovalRule;
import com.nexmemo.web.service.ApprovalRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/approval-rules")
@RequiredArgsConstructor
public class ApprovalRuleController {

    private final ApprovalRuleService approvalRuleService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApprovalRule>>> getAllRules() {
        return ResponseEntity.ok(ApiResponse.success(approvalRuleService.getAllRules()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalRule>> getRuleById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(approvalRuleService.getRuleById(id)));
    }

    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ApprovalRule>>> getByCategory(@PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(approvalRuleService.getRulesByCategory(categoryId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApprovalRule>> createRule(@Valid @RequestBody ApprovalRule rule) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(approvalRuleService.createRule(rule)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ApprovalRule>> updateRule(@PathVariable Long id, @Valid @RequestBody ApprovalRule rule) {
        return ResponseEntity.ok(ApiResponse.success("Approval rule updated", approvalRuleService.updateRule(id, rule)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        approvalRuleService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success("Approval rule deleted", null));
    }
}
