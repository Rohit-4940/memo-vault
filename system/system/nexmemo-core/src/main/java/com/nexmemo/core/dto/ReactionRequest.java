package com.nexmemo.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request to add an emoji reaction to a message.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionRequest {

    @NotBlank(message = "emoji is required")
    @Size(max = 32, message = "emoji must be at most 32 characters")
    private String emoji;
}
