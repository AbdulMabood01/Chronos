package com.maxwell.chronos.dto;

import com.maxwell.chronos.enums.TimesheetCorrectionStatus;
import java.time.LocalDateTime;

public record TimesheetCorrectionRequestDTO(Long id, Long timesheetId, Long projectId, String projectCode,
        Long userId, String userName, int year, int month, TimesheetCorrectionStatus status,
        String employeeComment, String adminComment, LocalDateTime createdAt, LocalDateTime decidedAt) {}
