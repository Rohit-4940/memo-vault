package com.nexmemo.security.repository;

import com.nexmemo.core.entity.ChatConversationLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationLabelRepository extends JpaRepository<ChatConversationLabel, Long> {

    List<ChatConversationLabel> findByUserIdOrderByNameAsc(Long userId);

    Optional<ChatConversationLabel> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    long countByUserId(Long userId);
}
