package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.PaginatedRequest;
import com.nexmemo.core.entity.Branch;
import com.nexmemo.security.dto.BranchDto;
import com.nexmemo.security.repository.BranchRepository;
import com.nexmemo.web.service.BranchService;
import com.nexmemo.web.service.PaginatedQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final PaginatedQueryService paginatedQueryService;
    private final BranchRepository branchRepository;

    @GetMapping("/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<Branch>> findPaginated(PaginatedRequest request) {
        return ResponseEntity.ok(paginatedQueryService.query(branchRepository, request, List.of("name", "branchCode", "branchEmail")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchDto>>> getAllBranches() {
        return ResponseEntity.ok(ApiResponse.success(branchService.getAllBranches()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchDto>> getBranch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranchById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchDto>> createBranch(@Valid @RequestBody BranchDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(branchService.createBranch(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<BranchDto>> updateBranch(@PathVariable Long id, @Valid @RequestBody BranchDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Branch updated", branchService.updateBranch(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(@PathVariable Long id) {
        branchService.deleteBranch(id);
        return ResponseEntity.ok(ApiResponse.success("Branch deleted", null));
    }
}
