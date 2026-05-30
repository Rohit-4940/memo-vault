package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoTemplate;
import com.nexmemo.web.service.MemoTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memo-templates")
@RequiredArgsConstructor
public class MemoTemplateController {

    private final MemoTemplateService memoTemplateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemoTemplate>>> getAllTemplates() {
        return ResponseEntity.ok(ApiResponse.success(memoTemplateService.getAllTemplates()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoTemplate>> getTemplate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoTemplateService.getTemplateById(id)));
    }

    @GetMapping("/by-department/{deptId}")
    public ResponseEntity<ApiResponse<List<MemoTemplate>>> getByDepartment(@PathVariable Long deptId) {
        return ResponseEntity.ok(ApiResponse.success(memoTemplateService.findByDepartmentId(deptId)));
    }

    @GetMapping("/by-memo-type/{typeId}")
    public ResponseEntity<ApiResponse<List<MemoTemplate>>> getByMemoType(@PathVariable Long typeId) {
        return ResponseEntity.ok(ApiResponse.success(memoTemplateService.findByMemoTypeId(typeId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<MemoTemplate>> createTemplate(@Valid @RequestBody MemoTemplate template) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(memoTemplateService.createTemplate(template)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<MemoTemplate>> updateTemplate(@PathVariable Long id, @Valid @RequestBody MemoTemplate template) {
        return ResponseEntity.ok(ApiResponse.success("Template updated", memoTemplateService.updateTemplate(id, template)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable Long id) {
        memoTemplateService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.success("Template deleted", null));
    }
}
