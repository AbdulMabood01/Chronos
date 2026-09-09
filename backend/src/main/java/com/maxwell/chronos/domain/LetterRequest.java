package com.maxwell.chronos.domain;

import com.maxwell.chronos.enums.LetterRequestType;
import com.maxwell.chronos.enums.VacationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "letter_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LetterRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private LetterRequestType requestType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private VacationStatus status;

    @Column(length = 255)
    private String recipientOrganization;

    @Column(length = 500)
    private String recipientAddress;

    @Column(length = 255)
    private String purpose;

    @Column(length = 200)
    private String requestedFullName;

    @Column(length = 120)
    private String requestedJobTitle;

    private LocalDate employmentStartDate;

    @Column(length = 120)
    private String immigrationCaseType;

    @Column(length = 120)
    private String destinationCountry;

    @Column(length = 255)
    private String consulateName;

    private LocalDate effectiveDate;

    private LocalDate travelStartDate;

    private LocalDate travelEndDate;

    private LocalDate vacationStartDate;

    private LocalDate vacationEndDate;

    @Column(length = 1000)
    private String notes;

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

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
