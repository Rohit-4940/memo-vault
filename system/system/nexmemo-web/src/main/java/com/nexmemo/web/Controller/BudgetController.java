package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.PaginatedRequest;
import com.nexmemo.core.entity.BudgetAllocation;
import com.nexmemo.core.repository.BudgetAllocationRepository;
import com.nexmemo.web.service.BudgetAllocationService;
import com.nexmemo.web.service.PaginatedQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetAllocationService budgetAllocationService;
    private final PaginatedQueryService paginatedQueryService;
    private final BudgetAllocationRepository budgetAllocationRepository;

    @GetMapping("/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<BudgetAllocation>> findPaginated(PaginatedRequest request) {
        return ResponseEntity.ok(paginatedQueryService.query(budgetAllocationRepository, request, List.of("description")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BudgetAllocation>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(budgetAllocationService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BudgetAllocation>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(budgetAllocationService.getById(id)));
    }

    @GetMapping("/by-fiscal-year/{fyId}")
    public ResponseEntity<ApiResponse<List<BudgetAllocation>>> getByFiscalYear(@PathVariable Long fyId) {
        return ResponseEntity.ok(ApiResponse.success(budgetAllocationService.getByFiscalYear(fyId)));
    }

    @GetMapping("/by-department/{deptId}/fiscal-year/{fyId}")
    public ResponseEntity<ApiResponse<BudgetAllocation>> getByDepartmentAndFiscalYear(@PathVariable Long deptId, @PathVariable Long fyId) {
        return ResponseEntity.ok(ApiResponse.success(budgetAllocationService.getByDepartmentAndFiscalYear(deptId, fyId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<BudgetAllocation>> create(@Valid @RequestBody BudgetAllocation ba) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(budgetAllocationService.create(ba)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<BudgetAllocation>> update(@PathVariable Long id, @Valid @RequestBody BudgetAllocation ba) {
        return ResponseEntity.ok(ApiResponse.success("Budget updated", budgetAllocationService.update(id, ba)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        budgetAllocationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Budget deleted", null));
    }
}
