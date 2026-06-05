package com.nexmemo.core.repository;

import com.nexmemo.core.entity.NotificationLog;
import com.nexmemo.core.enums.NotificationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long>, JpaSpecificationExecutor<NotificationLog> {
    List<NotificationLog> findByMemoId(Long memoId);
    List<NotificationLog> findByRecipientIdOrderBySentAtDesc(Long recipientId);
    List<NotificationLog> findByEventType(NotificationEventType eventType);
}
