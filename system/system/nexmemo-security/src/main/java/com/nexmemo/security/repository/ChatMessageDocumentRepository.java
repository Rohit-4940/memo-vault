package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessageDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageDocumentRepository extends JpaRepository<ChatMessageDocument, Long> {

    List<ChatMessageDocument> findByMessageIdAndIsRevokedFalseOrderByIdAsc(Long messageId);

    List<ChatMessageDocument> findByMessageIdInAndIsRevokedFalseOrderByIdAsc(Collection<Long> messageIds);

    Optional<ChatMessageDocument> findByIdAndMessageId(Long id, Long messageId);

    Optional<ChatMessageDocument> findByMessageIdAndDocumentIdAndIsRevokedFalse(Long messageId, Long documentId);

    /** Active shares of a document within a conversation — used to decide whether to drop the grant. */
    List<ChatMessageDocument> findByDocumentIdAndConversationIdAndIsRevokedFalse(Long documentId, Long conversationId);

    long countByDocumentIdAndConversationIdAndIsRevokedFalse(Long documentId, Long conversationId);
}
