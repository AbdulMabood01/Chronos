package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.TimesheetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimesheetDTO {
    private Long id;
    private Long userId;
    private String userName;
    private String userJobTitle;
    private Long primaryProjectId;
    private String primaryProjectCode;
    private String primaryProjectName;
    private Integer year;
    private Integer month;
    private TimesheetStatus status;
    private BigDecimal totalHours;
    private BigDecimal billRate;
    private BigDecimal effectiveBillRate;
    private BigDecimal approvedHourlyRate;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String approvedByName;
    private LocalDateTime rejectedAt;
    private String rejectedByName;
    private String rejectionReason;
    private Boolean editable;
    private Boolean pdfExportEligible;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<TimeEntryDTO> timeEntries;
    private Set<VacationDayDTO> vacationDays;
}
