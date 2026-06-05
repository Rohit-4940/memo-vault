package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.PaginatedRequest;
import com.nexmemo.core.entity.Vendor;
import com.nexmemo.core.repository.VendorRepository;
import com.nexmemo.web.service.PaginatedQueryService;
import com.nexmemo.web.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class VendorController {

    private final VendorService vendorService;
    private final PaginatedQueryService paginatedQueryService;
    private final VendorRepository vendorRepository;

    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<Vendor>> findPaginated(PaginatedRequest request) {
        return ResponseEntity.ok(paginatedQueryService.query(vendorRepository, request, List.of("name", "email", "phone")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Vendor>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(vendorService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(vendorService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Vendor>> create(@Valid @RequestBody Vendor vendor,
                                                       @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(vendorService.create(vendor, userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Vendor>> update(@PathVariable Long id, @Valid @RequestBody Vendor vendor) {
        return ResponseEntity.ok(ApiResponse.success("Vendor updated", vendorService.update(id, vendor)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        vendorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Vendor deleted", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Vendor>>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(ApiResponse.success(vendorService.search(query)));
    }
}
