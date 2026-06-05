package com.nexmemo.core.repository;

import com.nexmemo.core.entity.NotificationConfig;
import com.nexmemo.core.enums.NotificationChannel;
import com.nexmemo.core.enums.NotificationEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationConfigRepository extends JpaRepository<NotificationConfig, Long>, JpaSpecificationExecutor<NotificationConfig> {
    List<NotificationConfig> findByIsActiveTrue();
    Optional<NotificationConfig> findByEventTypeAndChannel(NotificationEventType eventType, NotificationChannel channel);
}
