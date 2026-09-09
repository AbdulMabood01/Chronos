package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.LetterRequestType;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateLetterRequest {
    private LetterRequestType requestType;
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
}
