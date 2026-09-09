package com.maxwell.chronos.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "time_entry_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntrySession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_entry_id", nullable = false)
    private TimeEntry timeEntry;

    @Column(name = "login_time", nullable = false)
    private LocalTime loginTime;

    @Column(name = "logout_time", nullable = false)
    private LocalTime logoutTime;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
