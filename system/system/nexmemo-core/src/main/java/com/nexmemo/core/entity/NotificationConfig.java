package com.nexmemo.core.entity;

import com.nexmemo.core.enums.NotificationChannel;
import com.nexmemo.core.enums.NotificationEventType;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "notification_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationConfig extends BaseEntity<Long> implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private NotificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(name = "template_subject")
    private String templateSubject;

    @Column(name = "template_body", columnDefinition = "TEXT")
    private String templateBody;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
