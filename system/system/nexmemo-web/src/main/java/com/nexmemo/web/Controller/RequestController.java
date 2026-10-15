package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.dto.PageResponse;
import com.nexmemo.core.dto.RequestTrackingDto;
import com.nexmemo.core.dto.RequestTypeDto;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.web.service.MemoService;
import com.nexmemo.web.service.RequestEngineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Phase 5b — self-service request API.
 *
 * <p>The "common request engine" surface: browse the catalogue of request types you may raise, then
 * create / edit / submit / cancel and track a request. A request is a memo underneath, so approval,
 * numbering, notification and audit all reuse the existing memo pipeline.</p>
 *
 * <p>All operations are {@code authenticated} and additionally authorized per-resource inside
 * {@link RequestEngineService} — the {@code {id}} is never trusted on its own.</p>
 *
 * @author : NexMemo
 **/
@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class RequestController {

    private final RequestEngineService requestEngineService;

    /** Request types the caller may raise, with their dynamic form schemas. */
    @GetMapping("/catalogue")
    public ResponseEntity<ApiResponse<List<RequestTypeDto>>> getCatalogue(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(requestEngineService.getCatalogue(userId)));
    }

    /** The caller's own requests (my requests), newest first. */
    @GetMapping("/my")
    public ResponseEntity<PageResponse<Memo>> getMyRequests(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        return ResponseEntity.ok(PageResponse.from(requestEngineService.getMyRequests(userId, pageable)));
    }

    /** Raise a request; set {@code submitNow} to submit it for approval immediately. */
    @PostMapping
    public ResponseEntity<ApiResponse<Memo>> createRequest(
            @Valid @RequestBody CreateRequestCommand command,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
                requestEngineService.createRequest(command.getRequest(), command.isSubmitNow(), userId)));
    }

    /** Read one of your requests. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Memo>> getRequest(@PathVariable Long id,
                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(requestEngineService.getRequest(id, userId)));
    }

    /** Edit a request that is still a draft / returned for revision. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Memo>> updateRequest(
            @PathVariable Long id,
            @Valid @RequestBody MemoService.UpdateMemoRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Request updated",
                requestEngineService.updateRequest(id, request, userId)));
    }

    /** Submit a draft request for approval. */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<Memo>> submitRequest(@PathVariable Long id,
                                                           @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Request submitted",
                requestEngineService.submitRequest(id, userId)));
    }

    /** Cancel a draft request. */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelRequest(@PathVariable Long id,
                                                           @AuthenticationPrincipal Long userId) {
        requestEngineService.cancelRequest(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Request cancelled", null));
    }

    /** Track a request: status, approval history and workflow stages. */
    @GetMapping("/{id}/tracking")
    public ResponseEntity<ApiResponse<RequestTrackingDto>> trackRequest(@PathVariable Long id,
                                                                        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(requestEngineService.trackRequest(id, userId)));
    }

    /** Raise-request body: the memo payload plus the optional immediate-submit flag. */
    @Data
    public static class CreateRequestCommand {
        @Valid
        @NotNull
        private MemoService.CreateMemoRequest request;
        private boolean submitNow = false;
    }
}
