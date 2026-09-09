package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.LetterRequestType;
import com.maxwell.chronos.enums.VacationStatus;
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
public class LetterRequestDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String employeeId;
    private String email;
    private String jobTitle;
    private LetterRequestType requestType;
    private VacationStatus status;
    private String recipientOrganization;
    private String recipientAddress;
    private String purpose;
    private String requestedFullName;
    private String requestedJobTitle;
    private LocalDate employmentStartDate;
    private String immigrationCaseType;
    private String destinationCountry;
    private String consulateName;
    private LocalDate effectiveDate;
    private LocalDate travelStartDate;
    private LocalDate travelEndDate;
    private LocalDate vacationStartDate;
    private LocalDate vacationEndDate;
    private String notes;
    private String letterPreview;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String approvedByName;
    private LocalDateTime rejectedAt;
    private String rejectedByName;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
