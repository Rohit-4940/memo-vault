package com.nexmemo.core.dto;

import lombok.*;

import java.util.List;

/**
 * Aggregated reaction for one emoji on one message.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatReactionSummaryDto {
    private String emoji;
    private int count;
    /** Whether the requesting user is one of the reactors. */
    private boolean reactedByMe;
    /** Reactors (bounded) — used by the UI as a tooltip. */
    private List<Long> userIds;
}
