package com.nexmemo.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request to edit an existing message. Only the author may edit and only within the
 * configured edit window (see {@code ChatMessageService.EDIT_WINDOW}).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditMessageRequest {

    @NotBlank(message = "Message content is required")
    @Size(max = 5000, message = "Message exceeds maximum length of 5000")
    private String message;
}
