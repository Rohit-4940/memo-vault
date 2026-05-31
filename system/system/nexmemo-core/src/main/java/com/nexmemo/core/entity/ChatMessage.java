package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderId;

    private Long receiverId;

    private Long memoId;

    private Long branchId;

    @Column(length = 30)
    private String conversationType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void prePersist() { this.createdDate = LocalDateTime.now(); }
}
