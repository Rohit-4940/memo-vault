package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoType;
import com.nexmemo.web.service.MemoTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memo-types")
@RequiredArgsConstructor
public class MemoTypeManageController {

    private final MemoTypeService memoTypeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MemoType>>> getAllMemoTypes() {
        return ResponseEntity.ok(ApiResponse.success(memoTypeService.getAllMemoTypes()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoType>> getMemoType(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoTypeService.getMemoTypeById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<MemoType>> createMemoType(@Valid @RequestBody MemoType dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(memoTypeService.createMemoType(dto)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<MemoType>> updateMemoType(@PathVariable Long id, @Valid @RequestBody MemoType dto) {
        return ResponseEntity.ok(ApiResponse.success("MemoType updated", memoTypeService.updateMemoType(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMemoType(@PathVariable Long id) {
        memoTypeService.deleteMemoType(id);
        return ResponseEntity.ok(ApiResponse.success("MemoType deleted", null));
    }
}
