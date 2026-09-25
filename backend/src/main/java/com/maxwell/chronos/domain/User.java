package com.maxwell.chronos.domain;

import com.maxwell.chronos.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

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
    @Builder.Default
    @Column(nullable = false, length = 100)
    private String timezone = "America/Chicago";

    @Column(length = 40)
    private String phoneNumber;

    @Column(length = 255)
    private String personalEmail;

    @Column(length = 200)
    private String addressLine1;

    @Column(length = 200)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String stateProvince;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 100)
    private String country;

    @Column(length = 3)
    private String bloodGroup;

    @Column(length = 200)
    private String emergencyContactName;

    @Column(length = 100)
    private String emergencyContactRelationship;

    @Column(length = 40)
    private String emergencyContactPhone;

    @Column(length = 255)
    private String emergencyContactEmail;
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
    private LocalDate joiningDate;

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
    private Boolean isActive;

    @Column(unique = true, length = 255)
    private String entraId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(length = 100)
    private String passwordHash;

    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordResetHash;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.time.Instant passwordResetExpiresAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.time.Instant passwordResetRequestedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private long credentialVersion;

    public String getAccountStatus() {
        return !Boolean.TRUE.equals(isActive) ? "INACTIVE" : passwordHash == null ? "INVITED" : "ACTIVE";
    }

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

    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }

    public boolean isProjectAdmin() {
        return UserRole.PROJECT_ADMIN.equals(this.role);
    }

    public boolean canManageProjects() {
        return isProjectAdmin();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
