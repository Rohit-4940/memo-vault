package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {

    List<ChatAttachment> findByMessageIdOrderByIdAsc(Long messageId);

    List<ChatAttachment> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

    Optional<ChatAttachment> findByIdAndMessageId(Long id, Long messageId);
}