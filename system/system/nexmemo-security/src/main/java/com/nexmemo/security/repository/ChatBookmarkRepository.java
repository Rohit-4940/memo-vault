package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatBookmark;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatBookmarkRepository extends JpaRepository<ChatBookmark, Long> {

    Optional<ChatBookmark> findByUserIdAndMessageId(Long userId, Long messageId);

    /** Bulk "which of these messages did this user save" — for history rendering. */
    List<ChatBookmark> findByUserIdAndMessageIdIn(Long userId, Collection<Long> messageIds);

    List<ChatBookmark> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    void deleteByUserIdAndMessageId(Long userId, Long messageId);
}