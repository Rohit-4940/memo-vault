package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessageMention;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageMentionRepository extends JpaRepository<ChatMessageMention, Long> {

    List<ChatMessageMention> findByMessageId(Long messageId);

    /** Bulk load for a page of messages (avoids N+1 when rendering history). */
    List<ChatMessageMention> findByMessageIdIn(Collection<Long> messageIds);

    Optional<ChatMessageMention> findByMessageIdAndMentionedUserId(Long messageId, Long mentionedUserId);

    void deleteByMessageId(Long messageId);

    /** The caller's "tagged" inbox: mentions addressed to one user, newest first. */
    List<ChatMessageMention> findByMentionedUserIdOrderByIdDesc(Long mentionedUserId, Pageable pageable);
}
