package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessageEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageEditHistoryRepository extends JpaRepository<ChatMessageEditHistory, Long> {

    /** Full edit trail for a message, newest edit first. */
    List<ChatMessageEditHistory> findByMessageIdOrderByEditedAtDesc(Long messageId);

    long countByMessageId(Long messageId);
}
