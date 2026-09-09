package com.maxwell.chronos.domain;

import com.maxwell.chronos.enums.TimesheetStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "timesheets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "year", "month"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timesheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_project_id")
    private Project primaryProject;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private TimesheetStatus status;

    @Column(name = "total_hours")
    private BigDecimal totalHours;

    @Column(name = "bill_rate")
    private BigDecimal billRate;

    @Column(name = "bill_rate_updated_at")
    private LocalDateTime billRateUpdatedAt;

    @Column(name = "approved_hourly_rate")
    private BigDecimal approvedHourlyRate;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private User approvedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by_id")
    private User rejectedBy;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "last_reminder_sent_at")
    private LocalDateTime lastReminderSentAt;

    @Column(name = "initial_reminder_sent_at")
    private LocalDateTime initialReminderSentAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "timesheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TimeEntry> timeEntries = new HashSet<>();

    public boolean isDraft() {
        return TimesheetStatus.DRAFT.equals(this.status);
    }

    public boolean isEditable() {
        return TimesheetStatus.DRAFT.equals(this.status)
                || TimesheetStatus.REJECTED.equals(this.status);
    }

    public boolean isLocked() {
        return TimesheetStatus.LOCKED.equals(this.status);
    }

    public void calculateTotalHours() {
        if (this.timeEntries != null) {
            this.totalHours = this.timeEntries.stream()
                    .map(TimeEntry::getHours)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
