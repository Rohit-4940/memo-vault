package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageReceiptRepository extends JpaRepository<ChatMessageReceipt, Long> {

    Optional<ChatMessageReceipt> findByMessageIdAndUserId(Long messageId, Long userId);

    List<ChatMessageReceipt> findByMessageId(Long messageId);

    /** Bulk lookup used to materialize a delivery/read range without N queries. */
    List<ChatMessageReceipt> findByMessageIdInAndUserId(Collection<Long> messageIds, Long userId);

    long countByMessageIdAndReadAtIsNotNull(Long messageId);
}
