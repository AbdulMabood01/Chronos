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
