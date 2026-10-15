package com.nexmemo.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoVersion;
import com.nexmemo.core.repository.MemoVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase 4c — wires the previously dead {@code memo_version} capability: snapshots a memo's
 * content + dynamic form values so history is available through {@code GET /memo-versions/...}.
 * A snapshot is written at creation (v1); future edit flows append subsequent versions.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoVersionService {

    private final MemoVersionRepository memoVersionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MemoVersion snapshot(Memo memo, Long actorId) {
        int next = memoVersionRepository.findTopByMemoIdOrderByVersionNumberDesc(memo.getId())
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        MemoVersion version = new MemoVersion();
        version.setMemo(memo);
        version.setVersionNumber(next);
        version.setContentJson(toJson(snapshotPayload(memo)));
        version.setCreatedBy(actorId != null ? actorId : 0L);
        version.setCreatedDate(LocalDateTime.now());

        MemoVersion saved = memoVersionRepository.save(version);
        log.info("MemoVersion v{} snapshot created for memo {}", saved.getVersionNumber(), memo.getId());
        return saved;
    }

    private Map<String, Object> snapshotPayload(Memo memo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memoNo", memo.getMemoNo());
        payload.put("title", memo.getTitle());
        payload.put("description", memo.getDescription());
        payload.put("content", memo.getContent());
        payload.put("amount", memo.getAmount());
        payload.put("formValues", parseJson(memo.getFormValues()));
        payload.put("memoTypeId", memo.getMemoType() != null ? memo.getMemoType().getId() : null);
        return payload;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json; // legacy/opaque content survives as a string
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize memo snapshot", e);
        }
    }
}