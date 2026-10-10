package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private boolean platformAdmin;
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
    private String gender;
    private String race;
    private String ethnicity;
    private boolean profileCorrectionOpen;
    private Boolean profileDetailsSubmitted;

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
    private LocalDate joiningDate;
    private String ssnLast4;
    private String profileImageUrl;
    private Boolean profileCompleted;
    private String role;
    private List<String> roles;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean canReviewProjects;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean canViewProjects;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean canManageProjects;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean canCreateProjects;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean canSubmitWork;
    private Boolean isActive;
}
