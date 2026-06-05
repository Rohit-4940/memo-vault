package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.PaginatedRequest;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.dto.CreateUserRequest;
import com.nexmemo.security.dto.UserDto;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.security.service.UserProvisioningService;
import com.nexmemo.web.service.PaginatedQueryService;
import com.nexmemo.web.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminUserController {

    private final UserProvisioningService provisioningService;
    private final UserService userService;
    private final PaginatedQueryService paginatedQueryService;
    private final UserRepository userRepository;

    @GetMapping("/paginated")
    public ResponseEntity<PageResponse<User>> findPaginated(PaginatedRequest request) {
        return ResponseEntity.ok(paginatedQueryService.query(userRepository, request, List.of("name", "username", "email")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<User>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal Long adminId) {
        User created = provisioningService.createUser(request, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDto>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> updateUser(@PathVariable Long id, @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(ApiResponse.success("User updated", userService.updateUser(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted", null));
    }
}
