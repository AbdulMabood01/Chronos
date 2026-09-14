package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectAssignmentDTO {
    private Long id;
    private Long userId;
    private String employeeId;
    private String userName;
    private String email;
    private String jobTitle;
    private Boolean isActive;
    private BigDecimal plannedHours;
    private BigDecimal approvedHoursToDate;
    private boolean pendingApproval;
    private BigDecimal billRate;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime assignedAt;
}
