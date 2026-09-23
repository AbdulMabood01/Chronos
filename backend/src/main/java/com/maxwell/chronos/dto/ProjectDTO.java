package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.ProjectStatus;
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
public class ProjectDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Boolean isActive;
    private ProjectStatus status;
    private BigDecimal totalAllocatedHours;
    private Long projectManagerId;
    private String projectManagerName;
    private Long projectManagerHoursApproverId;
    private String projectManagerHoursApproverName;
    private long pendingApprovalCount;
    private List<ProjectAssignmentDTO> assignments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
