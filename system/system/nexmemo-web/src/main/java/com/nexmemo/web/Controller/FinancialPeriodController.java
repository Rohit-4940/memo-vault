package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.FinancialPeriod;
import com.nexmemo.web.service.FinancialPeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Financial period administration — open / close / lock lifecycle within a fiscal year (Phase 4b).
 *
 * @author : NexMemo
 **/
@RestController
@RequestMapping("/financial-periods")
@RequiredArgsConstructor
public class FinancialPeriodController {

    private final FinancialPeriodService financialPeriodService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FinancialPeriod>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FinancialPeriod>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getById(id)));
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<FinancialPeriod>> getCurrentOpenPeriod() {
        return financialPeriodService.getActiveOpenPeriod()
                .map(period -> ResponseEntity.ok(ApiResponse.success(period)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.success("No open financial period", null)));
    }

    @GetMapping("/fiscal-year/{fiscalYearId}")
    public ResponseEntity<ApiResponse<List<FinancialPeriod>>> getByFiscalYear(@PathVariable Long fiscalYearId) {
        return ResponseEntity.ok(ApiResponse.success(financialPeriodService.getByFiscalYear(fiscalYearId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPeriod>> create(@Valid @RequestBody FinancialPeriod period) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(financialPeriodService.create(period)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPeriod>> update(@PathVariable Long id,
                                                               @Valid @RequestBody FinancialPeriod period) {
        return ResponseEntity.ok(ApiResponse.success("FinancialPeriod updated", financialPeriodService.update(id, period)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        financialPeriodService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("FinancialPeriod deleted", null));
    }

    @PutMapping("/{id}/open")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPeriod>> open(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("FinancialPeriod opened", financialPeriodService.open(id)));
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPeriod>> close(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("FinancialPeriod closed", financialPeriodService.close(id)));
    }

    @PutMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPeriod>> lock(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("FinancialPeriod locked", financialPeriodService.lock(id)));
    }
}