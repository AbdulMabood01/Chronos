package com.maxwell.chronos.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "time_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "timesheet_id", nullable = false)
    private Timesheet timesheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false)
    private BigDecimal hours;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "timeEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    @Builder.Default
    private List<TimeEntrySession> sessions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void validateHours() {
        if (hours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Hours cannot be negative");
        }
        if (hours.compareTo(new BigDecimal("24")) > 0) {
            throw new IllegalArgumentException("Hours cannot exceed 24 per day");
        }
    }
}
