package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageReactionRepository extends JpaRepository<ChatMessageReaction, Long> {

    Optional<ChatMessageReaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    List<ChatMessageReaction> findByMessageIdOrderByCreatedDateAsc(Long messageId);

    /** Bulk load for a page of messages (avoids N+1 when rendering history). */
    List<ChatMessageReaction> findByMessageIdIn(Collection<Long> messageIds);

    long countByMessageIdAndEmoji(Long messageId, String emoji);
}
