package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.PaginatedRequest;
import com.nexmemo.core.entity.LeaveTracker;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.repository.LeaveTrackerRepository;
import com.nexmemo.web.service.LeaveTrackerService;
import com.nexmemo.web.service.PaginatedQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveTrackerController {

    private final LeaveTrackerService leaveTrackerService;
    private final PaginatedQueryService paginatedQueryService;
    private final LeaveTrackerRepository leaveTrackerRepository;

    @GetMapping("/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResponse<LeaveTracker>> findPaginated(PaginatedRequest request) {
        return ResponseEntity.ok(paginatedQueryService.query(leaveTrackerRepository, request, List.of("leaveType", "reason")));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaveTracker>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(leaveTrackerService.getAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveTracker>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(leaveTrackerService.getById(id)));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<LeaveTracker>>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(leaveTrackerService.getByUserId(userId)));
    }

    @GetMapping("/check/{userId}")
    public ResponseEntity<ApiResponse<Boolean>> isUserOnLeave(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(leaveTrackerService.isUserOnLeave(userId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LeaveTracker>> create(@Valid @RequestBody LeaveTracker leave,
                                                            @AuthenticationPrincipal Long userId) {
        User user = new User();
        user.setId(userId);
        leave.setUser(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(leaveTrackerService.create(leave)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTracker>> update(@PathVariable Long id, @Valid @RequestBody LeaveTracker leave) {
        return ResponseEntity.ok(ApiResponse.success("Leave updated", leaveTrackerService.update(id, leave)));
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTracker>> approveLeave(@PathVariable Long id,
                                                                  @AuthenticationPrincipal Long approverId) {
        return ResponseEntity.ok(ApiResponse.success("Leave approved", leaveTrackerService.approveLeave(id, approverId)));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveTracker>> rejectLeave(@PathVariable Long id,
                                                                 @RequestParam String reason,
                                                                 @AuthenticationPrincipal Long approverId) {
        return ResponseEntity.ok(ApiResponse.success("Leave rejected", leaveTrackerService.rejectLeave(id, approverId, reason)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        leaveTrackerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Leave deleted", null));
    }
}
