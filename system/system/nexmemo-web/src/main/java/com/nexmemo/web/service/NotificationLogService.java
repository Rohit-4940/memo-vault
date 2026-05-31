package com.nexmemo.web.service;

import com.nexmemo.core.entity.NotificationLog;
import com.nexmemo.core.enums.NotificationChannel;
import com.nexmemo.core.enums.NotificationEventType;
import com.nexmemo.core.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    public NotificationLog logNotification(Long memoId, Long recipientId, NotificationEventType eventType,
                                           NotificationChannel channel, String status, String errorMessage) {
        NotificationLog logEntry = new NotificationLog();
        if (memoId != null) {
            com.nexmemo.core.entity.Memo memo = new com.nexmemo.core.entity.Memo();
            memo.setId(memoId);
            logEntry.setMemo(memo);
        }
        logEntry.setRecipientId(recipientId);
        logEntry.setEventType(eventType);
        logEntry.setChannel(channel);
        logEntry.setSentAt(LocalDateTime.now());
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        return notificationLogRepository.save(logEntry);
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getNotificationsForMemo(Long memoId) {
        return notificationLogRepository.findByMemoId(memoId);
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> getNotificationsForUser(Long userId) {
        return notificationLogRepository.findByRecipientIdOrderBySentAtDesc(userId);
    }
}
