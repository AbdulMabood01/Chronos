package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.ProjectStatus;
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
public class SaveProjectRequest {
    private String code;
    private String name;
    private String description;
    private Boolean isActive;
    private ProjectStatus status;
    private BigDecimal totalAllocatedHours;
    private Long projectManagerId;
    private Long projectManagerHoursApproverId;
    private LocalDate projectManagerStartDate;
    private LocalDate projectManagerEndDate;
    private BigDecimal projectManagerBillRate;
    private BigDecimal projectManagerPlannedHours;
}
