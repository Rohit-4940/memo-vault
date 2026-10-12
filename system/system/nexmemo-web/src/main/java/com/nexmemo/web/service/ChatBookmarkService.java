package com.nexmemo.web.service;

import com.nexmemo.core.dto.ChatBookmarkDto;
import com.nexmemo.core.entity.ChatBookmark;
import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.ChatBookmarkRepository;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-user saved messages (Phase 2d bookmarking).
 *
 * Bookmarks are strictly self-scoped: every read/write key is {@code userId} derived from the
 * principal, so there is no cross-user IDOR surface.
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatBookmarkService {

    private static final int MAX_LIST_SIZE = 100;

    private final ChatBookmarkRepository bookmarkRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatConversationService conversationService;
    private final ChatAuthorizationService chatAuth;

    public ChatBookmarkDto add(Long userId, Long messageId) {
        ChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (message.getConversationId() == null) {
            throw new IllegalArgumentException("Only conversation messages can be bookmarked");
        }
        chatAuth.assertCanAccessConversation(userId, conversationService.requireConversation(message.getConversationId()));

        boolean exists = bookmarkRepository.findByUserIdAndMessageId(userId, messageId).isPresent();
        if (!exists) {
            bookmarkRepository.save(ChatBookmark.builder()
                    .userId(userId)
                    .messageId(messageId)
                    .conversationId(message.getConversationId())
                    .build());
        }
        log.debug("Message {} bookmarked by userId={} (already={})", messageId, userId, exists);
        return toDto(userId, message, exists ? null : message.getCreatedDate());
    }

    public void remove(Long userId, Long messageId) {
        bookmarkRepository.deleteByUserIdAndMessageId(userId, messageId);
    }

    public List<ChatBookmarkDto> list(Long userId, int size) {
        int capped = Math.min(Math.max(size, 1), MAX_LIST_SIZE);
        List<ChatBookmark> rows = bookmarkRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, capped));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> messageIds = rows.stream().map(ChatBookmark::getMessageId).distinct().toList();
        Map<Long, ChatMessage> messages = new LinkedHashMap<>();
        messageRepository.findAllById(messageIds).forEach(m -> messages.put(m.getId(), m));

        List<Long> senderIds = messages.values().stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsDeleted()))
                .map(ChatMessage::getSenderId).distinct().toList();
        Map<Long, String> senderNames = new LinkedHashMap<>();
        userRepository.findAllById(senderIds).forEach(u -> senderNames.put(u.getId(), u.getName()));

        List<ChatBookmarkDto> result = new java.util.ArrayList<>(rows.size());
        for (ChatBookmark row : rows) {
            ChatMessage message = messages.get(row.getMessageId());
            if (message == null || Boolean.TRUE.equals(message.getIsDeleted())) {
                continue; // never surface a bookmark to deleted content
            }
            result.add(ChatBookmarkDto.builder()
                    .messageId(row.getMessageId())
                    .conversationId(row.getConversationId())
                    .conversationTitle(conversationTitle(row.getConversationId()))
                    .senderId(message.getSenderId())
                    .senderName(senderNames.getOrDefault(message.getSenderId(), "User #" + message.getSenderId()))
                    .preview(preview(message.getMessage()))
                    .createdDate(row.getCreatedDate())
                    .build());
        }
        return result;
    }

    /** Which of the given messages are bookmarked by the viewer — for history rendering. */
    public Set<Long> bookmarkedMessageIds(Long userId, Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Set.of();
        }
        return bookmarkRepository.findByUserIdAndMessageIdIn(userId, messageIds).stream()
                .map(ChatBookmark::getMessageId)
                .collect(Collectors.toSet());
    }

    private String conversationTitle(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        return conversationService.requireConversation(conversationId).getTitle();
    }

    private ChatBookmarkDto toDto(Long userId, ChatMessage message, java.time.LocalDateTime createdDate) {
        String senderName = userRepository.findById(message.getSenderId())
                .map(User::getName)
                .orElse("User #" + message.getSenderId());
        return ChatBookmarkDto.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .conversationTitle(conversationTitle(message.getConversationId()))
                .senderId(message.getSenderId())
                .senderName(senderName)
                .preview(preview(message.getMessage()))
                .createdDate(createdDate)
                .build();
    }

    private static String preview(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }
}