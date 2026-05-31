package com.nexmemo.web.service;

import com.nexmemo.core.entity.ChatMessage;
import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.ChatMessageRepository;
import com.nexmemo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public ChatMessage sendMessage(Long senderId, Long receiverId, Long memoId, Long branchId, String conversationType, String message) {
        ChatMessage msg = ChatMessage.builder()
                .senderId(senderId)
                .receiverId(receiverId)
                .memoId(memoId)
                .branchId(branchId)
                .conversationType(conversationType != null ? conversationType : "DIRECT_MESSAGE")
                .message(message)
                .isRead(false)
                .build();
        ChatMessage saved = chatMessageRepository.save(msg);
        log.info("Chat message sent: senderId={} conversationType={} branchId={}", senderId, conversationType, branchId);

        if ("BRANCH_CHAT".equals(conversationType)) {
            notifyAdminsOnBranchMessage(senderId, branchId, message);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMemoMessages(Long memoId) {
        return chatMessageRepository.findByMemoIdOrderByCreatedDateAsc(memoId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getBranchMessages(Long branchId) {
        return chatMessageRepository.findByBranchIdOrderByCreatedDateAsc(branchId);
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
        List<ChatMessage> unread = chatMessageRepository.findByBranchIdOrderByCreatedDateAsc(branchId);
        for (ChatMessage msg : unread) {
            if (!msg.getIsRead()) {
                msg.setIsRead(true);
                chatMessageRepository.save(msg);
            }
        }
    }

    public void markAsRead(Long messageId) {
        chatMessageRepository.findById(messageId).ifPresent(msg -> {
            msg.setIsRead(true);
            chatMessageRepository.save(msg);
        });
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
