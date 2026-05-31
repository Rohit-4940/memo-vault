package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByMemoIdOrderByCreatedDateAsc(Long memoId);

    List<ChatMessage> findByBranchIdOrderByCreatedDateAsc(Long branchId);

    List<ChatMessage> findByConversationTypeOrderByCreatedDateDesc(String conversationType);

    Page<ChatMessage> findByBranchId(Long branchId, Pageable pageable);

    @Query("SELECT c FROM ChatMessage c WHERE (c.senderId = :userId AND c.receiverId = :otherId) OR (c.senderId = :otherId AND c.receiverId = :userId) ORDER BY c.createdDate DESC")
    Page<ChatMessage> findConversation(@Param("userId") Long userId, @Param("otherId") Long otherId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ChatMessage c WHERE c.receiverId = :userId AND c.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM ChatMessage c WHERE c.branchId IS NOT NULL AND c.conversationType = 'BRANCH_CHAT' AND c.isRead = false")
    long countUnreadBranchMessages();

    @Query("SELECT DISTINCT c.senderId FROM ChatMessage c WHERE c.receiverId = :userId ORDER BY MAX(c.createdDate) DESC")
    List<Long> findSendersByReceiverId(@Param("userId") Long userId);

    @Query("SELECT c FROM ChatMessage c WHERE c.branchId IS NOT NULL AND c.conversationType = 'BRANCH_CHAT' ORDER BY c.createdDate DESC")
    Page<ChatMessage> findAllBranchMessages(Pageable pageable);

    @Query("SELECT c FROM ChatMessage c WHERE c.branchId = :branchId AND c.conversationType = 'BRANCH_CHAT' ORDER BY c.createdDate DESC")
    Page<ChatMessage> findBranchMessages(@Param("branchId") Long branchId, Pageable pageable);

    @Query("SELECT DISTINCT c.branchId FROM ChatMessage c WHERE c.conversationType = 'BRANCH_CHAT'")
    List<Long> findDistinctBranchIds();
}
