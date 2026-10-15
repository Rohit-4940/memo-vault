package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatDraftRepository extends JpaRepository<ChatDraft, Long> {

    Optional<ChatDraft> findByConversationIdAndUserId(Long conversationId, Long userId);

    void deleteByConversationIdAndUserId(Long conversationId, Long userId);
}
