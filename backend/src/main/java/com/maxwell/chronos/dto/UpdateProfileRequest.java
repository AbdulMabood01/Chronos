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
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @Size(max = 255)
    @jakarta.validation.constraints.Email
    @NotBlank(message = "Personal email is required")
    private String personalEmail;

    @Size(max = 200)
    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    @Size(max = 200)
    private String addressLine2;

    @Size(max = 100)
    @NotBlank(message = "City is required")
    private String city;

    @Size(max = 100)
    @NotBlank(message = "State / Province is required")
    private String stateProvince;

    @Size(max = 20)
    @NotBlank(message = "Postal code is required")
    private String postalCode;

    @Size(max = 100)
    @NotBlank(message = "Country is required")
    private String country;

    @Size(max = 3)
    @Pattern(regexp = "^$|^(A|B|AB|O)[+-]$", message = "Choose a valid blood group")
    private String bloodGroup;

    @Size(max = 200)
    @NotBlank(message = "Emergency contact name is required")
    private String emergencyContactName;

    @Size(max = 100)
    @NotBlank(message = "Emergency contact relationship is required")
    private String emergencyContactRelationship;

    @Size(max = 40)
    @NotBlank(message = "Emergency contact phone is required")
    private String emergencyContactPhone;

    @Size(max = 255)
    @jakarta.validation.constraints.Email
    @NotBlank(message = "Emergency contact email is required")
    private String emergencyContactEmail;
    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Size(max = 120)
    private String jobTitle;

    @NotNull
    private LocalDate dateOfBirth;
    @Size(max=100) @NotBlank(message = "Gender is required")
    private String gender;
    @Size(max=100) @NotBlank(message = "Race is required")
    private String race;
    @Size(max=100) @NotBlank(message = "Ethnicity is required")
    private String ethnicity;
    @NotNull(message = "Joining date is required")
    private LocalDate joiningDate;

    @Pattern(regexp = "^$|\\d{4}", message = "SSN last 4 must be blank or exactly 4 digits")
    private String ssnLast4;

    @Size(max = 750000, message = "Profile photo is too large")
    private String profileImageUrl;
}
