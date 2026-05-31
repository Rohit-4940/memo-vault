package com.nexmemo.web.Controller;

import com.nexmemo.core.dto.ApiResponse;
import com.nexmemo.core.entity.MemoVersion;
import com.nexmemo.core.repository.MemoVersionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memo-versions")
@RequiredArgsConstructor
public class MemoVersionController {

    private final MemoVersionRepository memoVersionRepository;

    @GetMapping("/memo/{memoId}")
    public ResponseEntity<ApiResponse<List<MemoVersion>>> getVersions(@PathVariable Long memoId) {
        return ResponseEntity.ok(ApiResponse.success(memoVersionRepository.findByMemoIdOrderByVersionNumberDesc(memoId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemoVersion>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(memoVersionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MemoVersion not found: " + id))));
    }
}
