package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.CategoryDocumentRequirement;
import com.nexmemo.core.entity.MemoCategory;
import com.nexmemo.web.service.MemoCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class MemoCategoryController {

    private final MemoCategoryService memoCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemoCategory>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(memoCategoryService.getAllCategories()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoCategory>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoCategoryService.getCategoryById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MemoCategory>> create(@Valid @RequestBody MemoCategory category) {
        return ResponseEntity.ok(ApiResponse.created(memoCategoryService.createCategory(category)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoCategory>> update(@PathVariable Long id, @Valid @RequestBody MemoCategory category) {
        return ResponseEntity.ok(ApiResponse.success(memoCategoryService.updateCategory(id, category)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        memoCategoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    @GetMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<List<CategoryDocumentRequirement>>> getDocuments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoCategoryService.getDocumentRequirements(id)));
    }

    @PostMapping("/{id}/documents")
    public ResponseEntity<ApiResponse<CategoryDocumentRequirement>> addDocument(
            @PathVariable Long id, @Valid @RequestBody CategoryDocumentRequirement req) {
        req.setCategory(memoCategoryService.getCategoryById(id));
        return ResponseEntity.ok(ApiResponse.created(memoCategoryService.addDocumentRequirement(req)));
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<ApiResponse<Void>> removeDocument(@PathVariable Long docId) {
        memoCategoryService.removeDocumentRequirement(docId);
        return ResponseEntity.ok(ApiResponse.success("Document requirement removed", null));
    }
}
