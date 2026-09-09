package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectHoursDashboardDTO {
    private Long projectId;
    private String projectCode;
    private String projectName;
    private String projectManagerName;
    private BigDecimal plannedHours;
    private BigDecimal draftHours;
    private BigDecimal submittedHours;
    private BigDecimal approvedHours;
    private BigDecimal rejectedHours;
    private BigDecimal totalLoggedHours;
    private List<ProjectHoursEmployeeDTO> employees;
}
