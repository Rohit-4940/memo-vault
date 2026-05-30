package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.SystemConfig;
import com.nexmemo.web.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system-configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SystemConfig>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.getAllConfigs()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SystemConfig>> getConfigById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.getConfigById(id)));
    }

    @GetMapping("/key/{key}")
    public ResponseEntity<ApiResponse<SystemConfig>> getByKey(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(systemConfigService.getConfigByKey(key)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<SystemConfig>> createConfig(@Valid @RequestBody SystemConfig config) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(systemConfigService.createConfig(config)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<SystemConfig>> updateConfig(@PathVariable Long id, @Valid @RequestBody SystemConfig config) {
        return ResponseEntity.ok(ApiResponse.success("Config updated", systemConfigService.updateConfig(id, config)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable Long id) {
        systemConfigService.deleteConfig(id);
        return ResponseEntity.ok(ApiResponse.success("Config deleted", null));
    }

    @PostMapping("/seed-defaults")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> seedDefaults() {
        systemConfigService.createDefaultConfigs();
        return ResponseEntity.ok(ApiResponse.success("Default configs seeded", null));
    }
}
