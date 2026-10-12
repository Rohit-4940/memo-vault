package com.nexmemo.core.dto;

import lombok.*;

/**
 * Minimal, safe projection of a {@link com.nexmemo.core.entity.User} for chat UI.
 *
 * SECURITY: deliberately excludes email, username, password, role, branch and every
 * other account/security field. The legacy chat endpoint returned the full {@code User}
 * entity to any authenticated caller, leaking PII. Only expose what the chat UI needs:
 * an id to key against and a display name.
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatUserDto {
    private Long id;
    private String name;
}
