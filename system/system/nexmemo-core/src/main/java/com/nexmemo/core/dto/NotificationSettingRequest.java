package com.nexmemo.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Per-conversation notification preference (ALL / MENTIONS / MUTE).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettingRequest {

    @NotBlank(message = "level is required")
    private String level;
}