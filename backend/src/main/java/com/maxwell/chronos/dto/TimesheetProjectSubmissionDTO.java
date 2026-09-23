package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.TimesheetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetProjectSubmissionDTO {
    private Long id;
    private Long timesheetId;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private Long projectManagerId;
    private String projectManagerName;
    private String projectManagerHoursApproverName;
    private String routedApproverName;
    private Long routedApproverId;
    private Long userId;
    private String userName;
    private String userJobTitle;
    private Integer year;
    private Integer month;
    private TimesheetStatus status;
    private BigDecimal totalHours;
    private BigDecimal loggedHoursToDate;
    private BigDecimal plannedHours;
    private BigDecimal remainingHours;
    private BigDecimal billRate;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String approvedByName;
    private LocalDateTime rejectedAt;
    private String rejectedByName;
    private String rejectionReason;
    private Boolean editable;
    private Boolean pdfExportEligible;
    private List<TimeEntryDTO> timeEntries;
}
