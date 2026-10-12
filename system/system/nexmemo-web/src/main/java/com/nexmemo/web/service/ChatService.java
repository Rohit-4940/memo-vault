package com.nexmemo.web.service;

import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ChatAuthorizationService chatAuthorizationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatMessage sendMessage(Long senderId, Long receiverId, Long memoId, Long branchId, String conversationType, String message) {
        String type = conversationType != null ? conversationType : "DIRECT_MESSAGE";
        ChatMessage msg = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .memoId(memoId)
                .branchId(branchId)
                .conversationType(type)
                .message(message)
                .isRead(false)
                .build();
        ChatMessage saved = chatMessageRepository.save(msg);
        log.info("Chat message sent: senderId={} conversationType={} branchId={}", senderId, type, branchId);

        if ("BRANCH_CHAT".equals(type)) {
            notifyAdminsOnBranchMessage(senderId, branchId, message);
            messagingTemplate.convertAndSend("/topic/branch/" + branchId, saved);
        } else if ("DIRECT_MESSAGE".equals(type) && receiverId != null) {
            messagingTemplate.convertAndSend("/queue/messages/" + receiverId, saved);
        }

        messagingTemplate.convertAndSend("/topic/notifications/" + senderId, saved);
        if (receiverId != null) {
            messagingTemplate.convertAndSend("/topic/notifications/" + receiverId, saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMemoMessages(Long memoId) {
        return notDeleted(chatMessageRepository.findByMemoIdOrderByCreatedDateAsc(memoId));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getBranchMessages(Long branchId) {
        return notDeleted(chatMessageRepository.findByBranchIdOrderByCreatedDateAsc(branchId));
    }

    /** Soft-deleted (moderated) messages must not be surfaced through the legacy endpoints. */
    private static List<ChatMessage> notDeleted(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getIsDeleted()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> getBranchMessagesPaginated(Long branchId, Pageable pageable) {
        return chatMessageRepository.findBranchMessages(branchId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> getConversation(Long userId, Long otherId, Pageable pageable) {
        return chatMessageRepository.findConversation(userId, otherId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return chatMessageRepository.countUnreadByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadBranchMessagesCount() {
        return chatMessageRepository.countUnreadBranchMessages();
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> getAllBranchMessages(Pageable pageable) {
        return chatMessageRepository.findAllBranchMessages(pageable);
    }

    @Transactional(readOnly = true)
    public List<Long> getActiveBranchIds() {
        return chatMessageRepository.findDistinctBranchIds();
    }

    public void markBranchMessagesAsRead(Long branchId) {
        int updated = chatMessageRepository.markBranchMessagesRead(branchId);
        log.debug("Marked {} branch messages as read for branchId={}", updated, branchId);
    }

    /**
     * Marks a single message as read. Only the intended recipient, an authorized member of
     * the owning branch, or an admin may do so. This closes the previous hole where any
     * authenticated caller could mark any message id as read (and probe for existence).
     */
    public void markAsRead(Long messageId, Long userId) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        chatAuthorizationService.assertCanAcknowledgeMessage(userId, msg);
        chatMessageRepository.markMessageRead(messageId);
    }

    private void notifyAdminsOnBranchMessage(Long senderId, Long branchId, String message) {
        List<User> admins = userRepository.findByRole_RoleName("SUPER_ADMIN");
        admins.addAll(userRepository.findByRole_RoleName("ADMIN"));
        String shortMsg = message.length() > 100 ? message.substring(0, 100) + "..." : message;
        for (User admin : admins) {
            if (!admin.getId().equals(senderId)) {
                notificationService.createNotification(
                        admin.getId(),
                        "New branch chat message",
                        "Branch #" + branchId + ": " + shortMsg,
                        "CHAT_BRANCH",
                        branchId,
                        "BRANCH"
                );
            }
        }
        log.info("Notified {} admins about branch chat message from userId={} branchId={}", admins.size(), senderId, branchId);
    }
}
