package com.nexmemo.core.dto;

import lombok.*;

/**
 * Create/rename payload for a conversation label (Enterprise Phase 5).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveChatLabelRequest {
    private String name;
    private String color;
}
