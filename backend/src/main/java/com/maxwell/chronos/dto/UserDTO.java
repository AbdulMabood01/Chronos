package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class UserDTO {
    private boolean platformAdmin;
    private String accountStatus;
    private boolean adminLocked;
    private java.time.Instant lockedUntil;
    private String timezone;
    private String phoneNumber;

    private String personalEmail;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String stateProvince;

    private String postalCode;

    private String country;

    private String bloodGroup;

    private String emergencyContactName;

    private String emergencyContactRelationship;

    private String emergencyContactPhone;

    private String emergencyContactEmail;
    private Long id;
    private String employeeId;
    private String firstName;
    private String lastName;
    private String jobTitle;
    private String gender;
    private String race;
    private String ethnicity;
    private boolean profileCorrectionOpen;
    private Boolean profileDetailsSubmitted;
    private LocalDate dateOfBirth;
    private LocalDate joiningDate;
    private String ssnLast4;
    private String profileImageUrl;
    private Boolean profileCompleted;
    private String email;
    private UserRole role;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
