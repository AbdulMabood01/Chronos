package com.maxwell.chronos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntryDTO {
    private Long id;
    private Long timesheetId;
    private Long projectId;
    private String projectCode;
    private String projectName;
    private LocalDate entryDate;
    private BigDecimal hours;
    private String notes;
    private List<TimeEntrySessionDTO> sessions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
