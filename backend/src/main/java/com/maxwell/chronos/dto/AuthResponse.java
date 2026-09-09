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
    private Boolean isActive;
}
