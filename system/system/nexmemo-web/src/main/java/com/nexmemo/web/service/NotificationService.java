package com.nexmemo.web.service;

import com.nexmemo.core.entity.Notification;
import com.nexmemo.security.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification createNotification(Long userId, String title, String message, String type, Long referenceId, String referenceType) {
        Notification notif = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .isRead(false)
                .build();
        Notification saved = notificationRepository.save(notif);
        log.info("Notification created: userId={} type={} refId={}", userId, type, referenceId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedDateDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId);
    }

    public int markAsRead(Long id, Long userId) {
        return notificationRepository.markAsRead(id, userId);
    }
}
