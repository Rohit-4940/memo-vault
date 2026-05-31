package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.ExpenseCategory;
import com.nexmemo.web.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseCategory>>> getAllCategories() {
        return ResponseEntity.ok(ApiResponse.success(expenseCategoryService.getAllCategories()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ExpenseCategory>> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(expenseCategoryService.getCategoryById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseCategory>> createCategory(@Valid @RequestBody ExpenseCategory category) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(expenseCategoryService.createCategory(category)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<ExpenseCategory>> updateCategory(@PathVariable Long id, @Valid @RequestBody ExpenseCategory category) {
        return ResponseEntity.ok(ApiResponse.success("ExpenseCategory updated", expenseCategoryService.updateCategory(id, category)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        expenseCategoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("ExpenseCategory deleted", null));
    }

    @GetMapping("/by-type/{memoTypeId}")
    public ResponseEntity<ApiResponse<List<ExpenseCategory>>> getByMemoType(@PathVariable Long memoTypeId) {
        return ResponseEntity.ok(ApiResponse.success(Collections.emptyList()));
    }
}
