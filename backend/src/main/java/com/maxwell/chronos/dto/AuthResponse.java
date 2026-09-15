package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
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
    private String email;
    private String firstName;
    private String lastName;
    private String jobTitle;
    private LocalDate dateOfBirth;
    private String ssnLast4;
    private String profileImageUrl;
    private Boolean profileCompleted;
    private String role;
    private boolean canReviewProjects;
    private Boolean isActive;
}
