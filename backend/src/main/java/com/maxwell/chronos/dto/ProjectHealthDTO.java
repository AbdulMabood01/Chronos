package com.maxwell.chronos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectHealthDTO(Long projectId, String projectCode, String projectName,
        String status, LocalDate evaluatedOn, boolean monitored,
        BigDecimal allocatedHours, BigDecimal plannedHours, BigDecimal loggedHours,
        BigDecimal remainingHours, Double hoursUtilization, LocalDate assignmentEndDate,
        int activeResources, int overallocatedResources, long missingTimesheets,
        long pendingApprovals, List<Signal> signals, List<String> coverageNotes) {
    public record Signal(String code, String severity, String message) {}
}
