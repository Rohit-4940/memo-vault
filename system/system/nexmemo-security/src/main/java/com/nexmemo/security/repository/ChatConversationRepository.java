package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatConversation;
import com.nexmemo.core.enums.ChatConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository
        extends JpaRepository<ChatConversation, Long>, JpaSpecificationExecutor<ChatConversation> {

    /** Idempotent get-or-create lookup for every non-GROUP conversation type. */
    Optional<ChatConversation> findByDedupeKey(String dedupeKey);

    Optional<ChatConversation> findByConversationTypeAndMemoId(ChatConversationType type, Long memoId);

    Optional<ChatConversation> findByConversationTypeAndBranchId(ChatConversationType type, Long branchId);

    Optional<ChatConversation> findByConversationTypeAndDepartmentId(ChatConversationType type, Long departmentId);

    // ── Phase 2i: search scope ─────────────────────────────────────────────────

    @Query("SELECT c.id FROM ChatConversation c WHERE c.conversationType = 'BRANCH' AND c.branchId IN :branchIds")
    List<Long> findIdsByConversationTypeAndBranchIdIn(@Param("branchIds") List<Long> branchIds);

    @Query("SELECT c.id FROM ChatConversation c WHERE c.conversationType = 'DEPARTMENT' AND c.departmentId IN :departmentIds")
    List<Long> findIdsByConversationTypeAndDepartmentIdIn(@Param("departmentIds") List<Long> departmentIds);
}
