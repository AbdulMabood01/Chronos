package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}
