package com.maxwell.chronos.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String notificationType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private Boolean isRead;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
