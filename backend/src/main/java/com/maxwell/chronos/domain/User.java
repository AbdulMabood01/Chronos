package com.maxwell.chronos.domain;

import com.maxwell.chronos.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String employeeId;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 120)
    private String jobTitle;

    private LocalDate dateOfBirth;

    @Column(length = 4)
    private String ssnLast4;

    @Column(columnDefinition = "TEXT")
    private String profileImageUrl;

    @Column(nullable = false)
    private Boolean profileCompleted;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private BigDecimal hourlyRate;

    @Column(name = "admin_override_hourly_rate")
    private BigDecimal adminOverrideHourlyRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rate_overridden_by_id")
    private User rateOverriddenBy;

    @Column(name = "rate_overridden_at")
    private LocalDateTime rateOverriddenAt;

    @Column(name = "rate_override_reason", length = 500)
    private String rateOverrideReason;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(unique = true, length = 255)
    private String entraId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Timesheet> timesheets = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<VacationRequest> vacationRequests = new HashSet<>();

    public boolean isSuperAdmin() {
        return UserRole.SUPER_ADMIN.equals(this.role);
    }

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }

    public boolean canManageProjects() {
        return isAdmin();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public BigDecimal getEffectiveHourlyRate() {
        return adminOverrideHourlyRate != null ? adminOverrideHourlyRate : hourlyRate;
    }

    public boolean hasAdminRateOverride() {
        return adminOverrideHourlyRate != null;
    }
}
