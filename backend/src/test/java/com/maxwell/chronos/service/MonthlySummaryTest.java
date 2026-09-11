package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthlySummaryTest {
    private final TimesheetRepository sheets = mock(TimesheetRepository.class);
    private final TimesheetProjectSubmissionRepository submissions = mock(TimesheetProjectSubmissionRepository.class);
    private final ProjectAssignmentRepository assignments = mock(ProjectAssignmentRepository.class);
    private final ReportService service = new ReportService(sheets, submissions, assignments, mock(VacationRequestRepository.class));
    private final User employee = User.builder().id(1L).role(UserRole.EMPLOYEE).firstName("Test").lastName("Employee").build();
    private final Timesheet sheet = Timesheet.builder().id(2L).user(employee).status(TimesheetStatus.APPROVED).build();

    @Test void missingProjectRateDoesNotFailSummaryOrUsePartialWeightedRate() {
        when(sheets.findByYearAndMonth(2026, 9)).thenReturn(List.of(sheet));
        when(submissions.findByTimesheetId(2L)).thenReturn(List.of(
                TimesheetProjectSubmission.builder().status(TimesheetStatus.APPROVED).totalHours(BigDecimal.TEN).approvedBillRate(BigDecimal.TEN).build(),
                TimesheetProjectSubmission.builder().status(TimesheetStatus.APPROVED).totalHours(BigDecimal.TEN).build()));
        var rows = service.getMonthlySummary(2026, 9);
        assertEquals(1, rows.size());
        assertNull(rows.getFirst().get("effectiveRate"));
        assertEquals(true, rows.getFirst().get("exportEligible"));
        assertEquals("Test Employee", rows.getFirst().get("employee"));
        verifyNoInteractions(assignments);
    }

    @Test void missingLegacyMonthlyRateDoesNotFailSummary() {
        when(sheets.findByYearAndMonth(2026, 9)).thenReturn(List.of(sheet));
        var row = service.getMonthlySummary(2026, 9).getFirst();
        assertNull(row.get("effectiveRate"));
        assertEquals(true, row.get("exportEligible"));
    }

    @Test void recordedZeroRateIsPreserved() {
        when(sheets.findByYearAndMonth(2026, 9)).thenReturn(List.of(sheet));
        when(submissions.findByTimesheetId(2L)).thenReturn(List.of(
                TimesheetProjectSubmission.builder().status(TimesheetStatus.APPROVED).totalHours(BigDecimal.TEN).approvedBillRate(BigDecimal.ZERO).build()));
        var rate = (BigDecimal) service.getMonthlySummary(2026, 9).getFirst().get("effectiveRate");
        assertEquals(0, rate.signum());
    }

    @Test void draftOrRejectedProjectSubmissionsAreNotExportEligible() {
        when(sheets.findByYearAndMonth(2026, 9)).thenReturn(List.of(sheet));
        when(submissions.findByTimesheetId(2L)).thenReturn(List.of(
                TimesheetProjectSubmission.builder().status(TimesheetStatus.DRAFT).totalHours(BigDecimal.TEN).build(),
                TimesheetProjectSubmission.builder().status(TimesheetStatus.REJECTED).totalHours(BigDecimal.TEN).build()));
        assertEquals(false, service.getMonthlySummary(2026, 9).getFirst().get("exportEligible"));
    }
}
