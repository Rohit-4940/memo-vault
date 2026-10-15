package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatConversationLabelAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationLabelAssignmentRepository
        extends JpaRepository<ChatConversationLabelAssignment, Long> {

    List<ChatConversationLabelAssignment> findByLabelId(Long labelId);

    List<ChatConversationLabelAssignment> findByLabelIdIn(Collection<Long> labelIds);

    List<ChatConversationLabelAssignment> findByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ChatConversationLabelAssignment> findByLabelIdAndConversationId(Long labelId, Long conversationId);

    void deleteByLabelId(Long labelId);
}
