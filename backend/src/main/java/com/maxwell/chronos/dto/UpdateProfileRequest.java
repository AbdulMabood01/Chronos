package com.maxwell.chronos.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @Size(max = 100)
    private String timezone;

    @Size(max = 40)
    private String phoneNumber;

    @Size(max = 255)
    @jakarta.validation.constraints.Email
    private String personalEmail;

    @Size(max = 200)
    private String addressLine1;

    @Size(max = 200)
    private String addressLine2;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String stateProvince;

    @Size(max = 20)
    private String postalCode;

    @Size(max = 100)
    private String country;

    @Size(max = 3)
    @Pattern(regexp = "^$|^(A|B|AB|O)[+-]$", message = "Choose a valid blood group")
    private String bloodGroup;

    @Size(max = 200)
    private String emergencyContactName;

    @Size(max = 100)
    private String emergencyContactRelationship;

    @Size(max = 40)
    private String emergencyContactPhone;

    @Size(max = 255)
    @jakarta.validation.constraints.Email
    private String emergencyContactEmail;
    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Size(max = 120)
    private String jobTitle;

    @NotNull
    private LocalDate dateOfBirth;

    @Pattern(regexp = "^$|\\d{4}", message = "SSN last 4 must be blank or exactly 4 digits")
    private String ssnLast4;

    @Size(max = 750000, message = "Profile photo is too large")
    private String profileImageUrl;
}
