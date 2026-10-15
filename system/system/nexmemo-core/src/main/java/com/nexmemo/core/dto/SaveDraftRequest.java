package com.nexmemo.core.dto;

import lombok.*;

/**
 * Autosave payload for a conversation draft (Enterprise Phase 5).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveDraftRequest {
    private String body;
}
