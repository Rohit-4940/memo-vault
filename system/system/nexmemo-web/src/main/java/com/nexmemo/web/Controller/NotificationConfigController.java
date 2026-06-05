package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.NotificationConfig;
import com.nexmemo.core.repository.NotificationConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification-configs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
public class NotificationConfigController {

    private final NotificationConfigRepository notificationConfigRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationConfig>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(notificationConfigRepository.findByIsActiveTrue()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationConfig>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(notificationConfigRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("NotificationConfig not found: " + id))));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationConfig>> create(@Valid @RequestBody NotificationConfig config) {
        return ResponseEntity.ok(ApiResponse.created(notificationConfigRepository.save(config)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationConfig>> update(@PathVariable Long id, @Valid @RequestBody NotificationConfig updated) {
        NotificationConfig config = notificationConfigRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("NotificationConfig not found: " + id));
        config.setEventType(updated.getEventType());
        config.setChannel(updated.getChannel());
        config.setTemplateSubject(updated.getTemplateSubject());
        config.setTemplateBody(updated.getTemplateBody());
        config.setIsActive(updated.getIsActive());
        return ResponseEntity.ok(ApiResponse.success(notificationConfigRepository.save(config)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        notificationConfigRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Config deleted", null));
    }
}
