package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserIdAndIsActiveTrue(Long conversationId, Long userId);

    List<ChatParticipant> findByConversationIdAndIsActiveTrue(Long conversationId);

    /** Bulk membership load across conversations (history rendering / search). */
    List<ChatParticipant> findByConversationIdInAndIsActiveTrue(java.util.Collection<Long> conversationIds);

    List<ChatParticipant> findByUserIdAndIsActiveTrue(Long userId);

    long countByConversationIdAndIsActiveTrue(Long conversationId);

    @Query("SELECT p.conversationId FROM ChatParticipant p WHERE p.userId = :userId AND p.isActive = true")
    List<Long> findActiveConversationIdsByUserId(@Param("userId") Long userId);
}
