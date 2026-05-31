package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.EscalationRule;
import com.nexmemo.web.service.EscalationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/escalation-rules")
@RequiredArgsConstructor
public class EscalationRuleController {

    private final EscalationService escalationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EscalationRule>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(escalationService.getAllRules()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EscalationRule>> create(@Valid @RequestBody EscalationRule rule) {
        return ResponseEntity.ok(ApiResponse.created(escalationService.createRule(rule)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EscalationRule>> update(@PathVariable Long id, @Valid @RequestBody EscalationRule rule) {
        return ResponseEntity.ok(ApiResponse.success(escalationService.updateRule(id, rule)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        escalationService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success("Rule deleted", null));
    }
}
