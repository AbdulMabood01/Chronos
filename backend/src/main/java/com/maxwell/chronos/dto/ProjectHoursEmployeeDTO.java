package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.TimesheetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectHoursEmployeeDTO {
    private Long userId;
    private String employeeId;
    private String userName;
    private String email;
    private String jobTitle;
    private BigDecimal plannedHours;
    private BigDecimal draftHours;
    private BigDecimal submittedHours;
    private BigDecimal approvedHours;
    private BigDecimal rejectedHours;
    private BigDecimal totalLoggedHours;
    private TimesheetStatus status;
    private Boolean assignmentActive;
    private LocalDate assignmentStartDate;
    private LocalDate assignmentEndDate;
    private Long timesheetId;
    private Long submissionId;
}
