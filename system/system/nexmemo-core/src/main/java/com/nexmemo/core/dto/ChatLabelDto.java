package com.nexmemo.core.dto;

import lombok.*;

import java.util.List;

/**
 * A user-scoped conversation label with the conversations it is currently applied to
 * (Enterprise Phase 5).
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatLabelDto {

    private Long id;
    private String name;
    private String color;
    /** Ids of the caller's conversations carrying this label. */
    private List<Long> conversationIds;
}
