package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.FiscalYear;
import com.nexmemo.web.service.FiscalYearService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fiscal-years")
@RequiredArgsConstructor
public class FiscalYearController {

    private final FiscalYearService fiscalYearService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FiscalYear>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(fiscalYearService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FiscalYear>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(fiscalYearService.getById(id)));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<FiscalYear>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(fiscalYearService.getActiveFiscalYear()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FiscalYear>> create(@Valid @RequestBody FiscalYear fy) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(fiscalYearService.create(fy)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FiscalYear>> update(@PathVariable Long id, @Valid @RequestBody FiscalYear fy) {
        return ResponseEntity.ok(ApiResponse.success("FiscalYear updated", fiscalYearService.update(id, fy)));
    }

    @PutMapping("/{id}/set-active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FiscalYear>> setActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("FiscalYear set as active", fiscalYearService.setActiveFiscalYear(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        fiscalYearService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("FiscalYear deleted", null));
    }
}
