package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatMessageDto;
import com.nexmemo.core.dto.ChatReportDto;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.ChatConversationRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.ChatParticipantRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Authorized message search (Phase 2i) and the admin moderation queue (Phase 2j).
 *
 * Search is <em>scoped</em>: candidates are conversations the caller can read — their participant
 * conversations plus BRANCH/DEPARTMENT channels of the groups they belong to. Admins search
 * everything. The actual text filter runs server-side, so results can never include a message
 * from a conversation the caller may not access.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatSearchService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ChatMessageRepository messageRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ChatAuthorizationService chatAuth;
    private final ChatMessageService messageService;

    public Page<ChatMessageDto> search(Long userId, String query, int page, int size) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty() || q.length() > 200) {
            return Page.empty();
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        List<Long> conversationIds = scopedConversationIds(userId);
        if (conversationIds.isEmpty()) {
            return Page.empty();
        }

        Page<ChatMessage> messages = messageRepository.searchInConversations(conversationIds, q, pageable);
        List<ChatMessageDto> content = messages.getContent().isEmpty()
                ? List.of()
                : messageService.renderMessages(userId, messages.getContent());
        log.debug("Search '{}' by userId={} → {} results in {} scoped conversations",
                q, userId, content.size(), conversationIds.size());
        return new PageImpl<>(content, pageable, messages.getTotalElements());
    }

    /** Admin-only moderation queue: problem reports (⚠ …) across every conversation. */
    public Page<ChatReportDto> adminReports(Long adminId, int page, int size) {
        if (!chatAuth.isAdmin(adminId)) {
            throw new AccessDeniedException("Admin role required");
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ChatMessage> messages = messageRepository.findProblemReports(pageable);
        List<ChatReportDto> content = new ArrayList<>(messages.getNumberOfElements());
        for (ChatMessage m : messages.getContent()) {
            String senderName = userRepository.findById(m.getSenderId())
                    .map(User::getName)
                    .orElse("User #" + m.getSenderId());
            content.add(ChatReportDto.builder()
                    .messageId(m.getId())
                    .conversationId(m.getConversationId())
                    .branchId(m.getBranchId())
                    .senderId(m.getSenderId())
                    .senderName(senderName)
                    .preview(ChatMentionService.preview(m.getMessage()))
                    .createdDate(m.getCreatedDate())
                    .build());
        }
        return new PageImpl<>(content, pageable, messages.getTotalElements());
    }

    /**
     * Every conversation id the caller may read: participants' conversations + the branch and
     * department channels of every group the caller belongs to. MEMO conversations are reached
     * via participation (access to a memo discussion creates a participant row on first join).
     */
    private List<Long> scopedConversationIds(Long userId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        ids.addAll(participantRepository.findActiveConversationIdsByUserId(userId));

        List<Long> branchIds = userRepository.findBranchIdsByUserId(userId);
        if (!branchIds.isEmpty()) {
            ids.addAll(conversationRepository.findIdsByConversationTypeAndBranchIdIn(branchIds));
        }
        List<Long> departmentIds = userRepository.findDepartmentIdsByUserId(userId);
        if (!departmentIds.isEmpty()) {
            ids.addAll(conversationRepository.findIdsByConversationTypeAndDepartmentIdIn(departmentIds));
        }
        return new ArrayList<>(ids);
    }
}