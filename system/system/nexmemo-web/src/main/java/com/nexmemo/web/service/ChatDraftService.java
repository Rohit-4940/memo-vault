package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatDraftDto;
import com.nexmemo.core.entity.ChatDraft;
import com.nexmemo.security.repository.ChatDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Private, per-user message drafts with autosave (Enterprise Phase 5).
 *
 * <p>A draft is scoped to {@code (conversation, author)} and is never broadcast or exposed to other
 * participants. Saving a blank body clears the draft; sending a message clears it server-side so a
 * stale draft can never be re-submitted.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatDraftService {

    /** Upper bound on a stored draft, mirroring the message body limit. */
    public static final int MAX_DRAFT_CHARS = 20_000;

    private final ChatDraftRepository draftRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;

    @Transactional(readOnly = true)
    public ChatDraftDto get(Long userId, Long conversationId) {
        requireAccess(userId, conversationId);
        return draftRepository.findByConversationIdAndUserId(conversationId, userId)
                .map(d -> ChatDraftDto.builder()
                        .conversationId(conversationId)
                        .body(d.getBody())
                        .updatedAt(d.getModifiedDate())
                        .build())
                .orElseGet(() -> ChatDraftDto.builder().conversationId(conversationId).build());
    }

    public ChatDraftDto save(Long userId, Long conversationId, String body) {
        requireAccess(userId, conversationId);
        if (body == null || body.isBlank()) {
            draftRepository.deleteByConversationIdAndUserId(conversationId, userId);
            return ChatDraftDto.builder().conversationId(conversationId).build();
        }
        String stored = body.length() > MAX_DRAFT_CHARS ? body.substring(0, MAX_DRAFT_CHARS) : body;
        ChatDraft draft = draftRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseGet(() -> ChatDraft.builder()
                        .conversationId(conversationId)
                        .userId(userId)
                        .build());
        draft.setBody(stored);
        ChatDraft saved = draftRepository.save(draft);
        return ChatDraftDto.builder()
                .conversationId(conversationId)
                .body(saved.getBody())
                .updatedAt(saved.getModifiedDate())
                .build();
    }

    /** Deletes the caller's draft for a conversation (called after a successful send). */
    public void clear(Long userId, Long conversationId) {
        if (userId == null || conversationId == null) {
            return;
        }
        draftRepository.deleteByConversationIdAndUserId(conversationId, userId);
    }

    private void requireAccess(Long userId, Long conversationId) {
        chatAuth.assertCanAccessConversation(userId,
                conversationService.requireConversation(conversationId));
    }
}
