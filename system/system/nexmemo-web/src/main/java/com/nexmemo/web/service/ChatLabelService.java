package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatLabelDto;
import com.nexmemo.core.entity.ChatConversationLabel;
import com.nexmemo.core.entity.ChatConversationLabelAssignment;
import com.nexmemo.security.repository.ChatConversationLabelAssignmentRepository;
import com.nexmemo.security.repository.ChatConversationLabelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-scoped conversation labels — the "mail-centre" folder overlay for chat (Enterprise Phase 5).
 *
 * <p>Labels are private organising aids, never a permission boundary: a label id belonging to
 * another user is treated as not found, and applying a label additionally requires access to the
 * underlying conversation.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatLabelService {

    public static final int MAX_NAME = 60;
    public static final int MAX_COLOR = 20;
    public static final int MAX_LABELS = 100;

    private final ChatConversationLabelRepository labelRepository;
    private final ChatConversationLabelAssignmentRepository assignmentRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;

    @Transactional(readOnly = true)
    public List<ChatLabelDto> list(Long userId) {
        List<ChatConversationLabel> labels = labelRepository.findByUserIdOrderByNameAsc(userId);
        if (labels.isEmpty()) {
            return List.of();
        }
        List<Long> ids = labels.stream().map(ChatConversationLabel::getId).toList();
        Map<Long, List<Long>> byLabel = new LinkedHashMap<>();
        for (ChatConversationLabelAssignment a : assignmentRepository.findByLabelIdIn(ids)) {
            byLabel.computeIfAbsent(a.getLabelId(), k -> new ArrayList<>()).add(a.getConversationId());
        }
        List<ChatLabelDto> out = new ArrayList<>(labels.size());
        for (ChatConversationLabel l : labels) {
            out.add(toDto(l, byLabel.getOrDefault(l.getId(), List.of())));
        }
        return out;
    }

    public ChatLabelDto create(Long userId, String name, String color) {
        String cleanName = requireName(name);
        if (labelRepository.existsByUserIdAndNameIgnoreCase(userId, cleanName)) {
            throw new IllegalArgumentException("A label with that name already exists");
        }
        if (labelRepository.countByUserId(userId) >= MAX_LABELS) {
            throw new IllegalArgumentException("Label limit (" + MAX_LABELS + ") reached");
        }
        ChatConversationLabel label = labelRepository.save(ChatConversationLabel.builder()
                .userId(userId)
                .name(cleanName)
                .color(cleanColor(color))
                .build());
        log.info("Chat label {} created by userId={}", label.getId(), userId);
        return toDto(label, List.of());
    }

    public ChatLabelDto update(Long userId, Long labelId, String name, String color) {
        ChatConversationLabel label = requireOwned(userId, labelId);
        if (name != null && !name.isBlank()) {
            String cleanName = requireName(name);
            if (!cleanName.equalsIgnoreCase(label.getName())
                    && labelRepository.existsByUserIdAndNameIgnoreCase(userId, cleanName)) {
                throw new IllegalArgumentException("A label with that name already exists");
            }
            label.setName(cleanName);
        }
        if (color != null) {
            label.setColor(cleanColor(color));
        }
        labelRepository.save(label);
        return toDto(label, assignmentRepository.findByLabelId(labelId).stream()
                .map(ChatConversationLabelAssignment::getConversationId).toList());
    }

    public void delete(Long userId, Long labelId) {
        ChatConversationLabel label = requireOwned(userId, labelId);
        assignmentRepository.deleteByLabelId(labelId);
        labelRepository.delete(label);
        log.info("Chat label {} deleted by userId={}", labelId, userId);
    }

    public void assign(Long userId, Long labelId, Long conversationId) {
        requireOwned(userId, labelId);
        chatAuth.assertCanAccessConversation(userId,
                conversationService.requireConversation(conversationId));
        if (assignmentRepository.findByLabelIdAndConversationId(labelId, conversationId).isPresent()) {
            return; // idempotent
        }
        assignmentRepository.save(ChatConversationLabelAssignment.builder()
                .labelId(labelId)
                .conversationId(conversationId)
                .userId(userId)
                .build());
    }

    public void unassign(Long userId, Long labelId, Long conversationId) {
        requireOwned(userId, labelId);
        assignmentRepository.findByLabelIdAndConversationId(labelId, conversationId)
                .ifPresent(assignmentRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<ChatLabelDto> labelsForConversation(Long userId, Long conversationId) {
        chatAuth.assertCanAccessConversation(userId,
                conversationService.requireConversation(conversationId));
        List<Long> labelIds = assignmentRepository.findByConversationIdAndUserId(conversationId, userId).stream()
                .map(ChatConversationLabelAssignment::getLabelId).toList();
        if (labelIds.isEmpty()) {
            return List.of();
        }
        List<ChatLabelDto> out = new ArrayList<>();
        for (ChatConversationLabel l : labelRepository.findAllById(labelIds)) {
            if (userId.equals(l.getUserId())) {
                out.add(toDto(l, List.of(conversationId)));
            }
        }
        return out;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatConversationLabel requireOwned(Long userId, Long labelId) {
        return labelRepository.findByIdAndUserId(labelId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Label not found"));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Label name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME) {
            throw new IllegalArgumentException("Label name is too long (max " + MAX_NAME + " characters)");
        }
        return trimmed;
    }

    private static String cleanColor(String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String trimmed = color.trim();
        return trimmed.length() > MAX_COLOR ? trimmed.substring(0, MAX_COLOR) : trimmed;
    }

    private ChatLabelDto toDto(ChatConversationLabel l, List<Long> conversationIds) {
        return ChatLabelDto.builder()
                .id(l.getId())
                .name(l.getName())
                .color(l.getColor())
                .conversationIds(conversationIds)
                .build();
    }
}
