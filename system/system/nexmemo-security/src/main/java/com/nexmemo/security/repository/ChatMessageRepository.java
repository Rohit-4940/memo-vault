package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long>, JpaSpecificationExecutor<ChatMessage> {
    List<ChatMessage> findByMemoIdOrderByCreatedDateAsc(Long memoId);

    List<ChatMessage> findByBranchIdOrderByCreatedDateAsc(Long branchId);

    List<ChatMessage> findByConversationTypeOrderByCreatedDateDesc(String conversationType);

    Page<ChatMessage> findByBranchId(Long branchId, Pageable pageable);

    @Query("SELECT c FROM ChatMessage c WHERE (c.senderId = :userId AND c.receiverId = :otherId) OR (c.senderId = :otherId AND c.receiverId = :userId) ORDER BY c.createdDate DESC")
    Page<ChatMessage> findConversation(@Param("userId") Long userId, @Param("otherId") Long otherId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM ChatMessage c WHERE c.receiverId = :userId AND c.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(c) FROM ChatMessage c WHERE c.branchId IS NOT NULL AND (c.conversationType = 'BRANCH_CHAT' OR c.conversationType IS NULL) AND c.isRead = false")
    long countUnreadBranchMessages();

    @Query("SELECT DISTINCT c.senderId FROM ChatMessage c WHERE c.receiverId = :userId ORDER BY MAX(c.createdDate) DESC")
    List<Long> findSendersByReceiverId(@Param("userId") Long userId);

    @Query("SELECT c FROM ChatMessage c WHERE c.branchId IS NOT NULL AND (c.conversationType = 'BRANCH_CHAT' OR c.conversationType IS NULL) ORDER BY COALESCE(c.createdDate, c.id) DESC")
    Page<ChatMessage> findAllBranchMessages(Pageable pageable);

    @Query("SELECT c FROM ChatMessage c WHERE c.branchId = :branchId AND (c.conversationType = 'BRANCH_CHAT' OR c.conversationType IS NULL) ORDER BY COALESCE(c.createdDate, c.id) DESC")
    Page<ChatMessage> findBranchMessages(@Param("branchId") Long branchId, Pageable pageable);

    @Query("SELECT DISTINCT c.branchId FROM ChatMessage c WHERE c.conversationType = 'BRANCH_CHAT' OR c.conversationType IS NULL")
    List<Long> findDistinctBranchIds();

    /**
     * Bulk mark-as-read for a branch. Avoids loading and saving every row (N updates).
     * Caller must run inside a transaction (see ChatService).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatMessage c SET c.isRead = true WHERE c.branchId = :branchId AND c.isRead = false")
    int markBranchMessagesRead(@Param("branchId") Long branchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatMessage c SET c.isRead = true WHERE c.id = :id AND c.isRead = false")
    int markMessageRead(@Param("id") Long id);

    // ── Phase 1a: conversation-scoped message queries ─────────────────────────

    Page<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    Page<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId, Pageable pageable);

    Optional<ChatMessage> findTopByConversationIdOrderByIdDesc(Long conversationId);

    Optional<ChatMessage> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);

    long countByConversationIdAndIdGreaterThanAndSenderIdNot(Long conversationId, Long id, Long senderId);

    long countByConversationIdAndSenderIdNot(Long conversationId, Long senderId);

    /**
     * Newest-first page of non-deleted messages (tombstones are excluded from history; the
     * tombstone is only surfaced to a client that already has the message cached).
     */
    Page<ChatMessage> findByConversationIdAndIsDeletedFalseOrderByIdDesc(Long conversationId, Pageable pageable);

    Page<ChatMessage> findByConversationIdAndIsDeletedFalseAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    /** Messages in a watermark range, oldest-first — used to materialize delivery/read receipts. */
    List<ChatMessage> findByConversationIdAndIdGreaterThanAndIdLessThanEqualOrderByIdAsc(
            Long conversationId, Long afterId, Long upToId);

    // ── Phase 2c: threads ──────────────────────────────────────────────────────

    /** Oldest-first replies to a parent message (deleted replies excluded). */
    List<ChatMessage> findByParentMessageIdAndIsDeletedFalseOrderByIdAsc(Long parentMessageId, Pageable pageable);

    List<ChatMessage> findByParentMessageIdAndIsDeletedFalseAndIdGreaterThanOrderByIdAsc(
            Long parentMessageId, Long afterId, Pageable pageable);

    // ── Phase 2d: pinned ───────────────────────────────────────────────────────

    Page<ChatMessage> findByConversationIdAndIsPinnedTrueAndIsDeletedFalseOrderByIdDesc(
            Long conversationId, Pageable pageable);

    // ── Phase 2i: scoped search ────────────────────────────────────────────────

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.conversationId IN :conversationIds
              AND m.isDeleted = false
              AND LOWER(m.message) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY m.id DESC
            """)
    Page<ChatMessage> searchInConversations(@Param("conversationIds") List<Long> conversationIds,
                                            @Param("q") String q, Pageable pageable);

    // ── Phase 2j: moderation queue ─────────────────────────────────────────────

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.message LIKE '⚠%' AND m.isDeleted = false AND m.conversationId IS NOT NULL
            ORDER BY m.id DESC
            """)
    Page<ChatMessage> findProblemReports(Pageable pageable);
}
