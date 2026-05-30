package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.Menu;
import com.nexmemo.web.service.MenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Menu>>> getAllMenus() {
        return ResponseEntity.ok(ApiResponse.success(menuService.getAllMenus()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Menu>> getMenu(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(menuService.getMenuById(id)));
    }

    @GetMapping("/by-role/{roleId}")
    public ResponseEntity<ApiResponse<List<Menu>>> getMenusByRole(@PathVariable Long roleId) {
        return ResponseEntity.ok(ApiResponse.success(menuService.getMenusByRoleId(roleId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Menu>> createMenu(@Valid @RequestBody Menu menu) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(menuService.createMenu(menu)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Menu>> updateMenu(@PathVariable Long id, @Valid @RequestBody Menu menu) {
        return ResponseEntity.ok(ApiResponse.success("Menu updated", menuService.updateMenu(id, menu)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.ok(ApiResponse.success("Menu deleted", null));
    }
}
