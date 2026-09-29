package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimesheetCorrectionRequestServiceTest {
    @Mock TimesheetCorrectionRequestRepository requests;
    @Mock TimesheetRepository timesheets;
    @Mock TimesheetProjectSubmissionRepository submissions;
    @Mock ProjectRepository projects;
    @Mock ProjectAssignmentRepository assignments;
    @Mock UserRepository users;
    @Mock TimesheetService timesheetService;
    @Mock NotificationService notifications;
    @Mock AuditService audit;
    @InjectMocks TimesheetCorrectionRequestService service;

    User employee, admin;
    Project project;
    Timesheet sheet;

    @BeforeEach void setup() {
        employee = User.builder().id(1L).firstName("Test").lastName("Employee").role(UserRole.EMPLOYEE).build();
        admin = User.builder().id(2L).role(UserRole.PROJECT_ADMIN).isActive(true).build();
        project = Project.builder().id(3L).code("P1").build();
        YearMonth month = YearMonth.now().minusMonths(1);
        sheet = Timesheet.builder().id(4L).user(employee).year(month.getYear()).month(month.getMonthValue())
                .status(TimesheetStatus.DRAFT).build();
        lenient().when(timesheets.findForUpdate(4L)).thenReturn(Optional.of(sheet));
        lenient().when(projects.findById(3L)).thenReturn(Optional.of(project));
        lenient().when(assignments.findByProjectIdAndUserId(3L, 1L)).thenReturn(Optional.of(ProjectAssignment.builder()
                .user(employee).project(project).startDate(month.atDay(1)).endDate(month.atEndOfMonth()).build()));
        lenient().when(requests.save(any())).thenAnswer(call -> {
            TimesheetCorrectionRequest saved = call.getArgument(0);
            if (saved.getId() == null) saved.setId(5L);
            return saved;
        });
        lenient().when(users.findByRole(UserRole.PROJECT_ADMIN)).thenReturn(List.of(admin));
    }

    @Test void employeeCanRequestPastDraftWithCommentAndProjectAdminIsNotified() {
        var result = service.request(4L, 3L, "  Missed July hours  ", employee);
        assertEquals(TimesheetCorrectionStatus.PENDING, result.status());
        assertEquals("Missed July hours", result.employeeComment());
        verify(notifications).createNotification(eq(2L), eq("TIMESHEET_CORRECTION_REQUESTED"), anyString(), anyString(), eq(5L), eq("TimesheetCorrectionRequest"));
    }

    @Test void requestDeadlineIsThirtyDaysAfterMonthEndAndDuplicateIsRejected() {
        YearMonth old = YearMonth.now().minusMonths(2);
        sheet.setYear(old.getYear());
        sheet.setMonth(old.getMonthValue());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.request(4L, 3L, "Missed hours", employee)).getMessage().contains("request deadline"));
        YearMonth eligible = YearMonth.now().minusMonths(1);
        sheet.setYear(eligible.getYear());
        sheet.setMonth(eligible.getMonthValue());
        when(requests.existsByTimesheetIdAndProjectIdAndStatus(4L, 3L, TimesheetCorrectionStatus.PENDING)).thenReturn(true);
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.request(4L, 3L, "Missed hours", employee)).getMessage().contains("already pending"));
    }

    @Test void approvedRequestOpensWindowAndDeclineRequiresComment() {
        sheet.setStatus(TimesheetStatus.LOCKED);
        var request = TimesheetCorrectionRequest.builder().id(5L).timesheet(sheet).project(project).user(employee)
                .status(TimesheetCorrectionStatus.PENDING).employeeComment("Correction needed").build();
        when(requests.findById(5L)).thenReturn(Optional.of(request));
        when(requests.findForUpdate(5L)).thenReturn(Optional.of(request));
        assertThrows(IllegalArgumentException.class, () -> service.decide(5L, false, " ", admin));
        assertEquals(TimesheetCorrectionStatus.PENDING, request.getStatus());
        var result = service.decide(5L, true, "Reviewed", admin);
        assertEquals(TimesheetCorrectionStatus.APPROVED, result.status());
        verify(timesheetService).openAfterApprovedRequest(4L, 3L, "Reviewed", 2L);
        verify(notifications, never()).createNotification(eq(1L), eq("TIMESHEET_CORRECTION_DECLINED"), anyString(), anyString(), eq(5L), eq("TimesheetCorrectionRequest"));
        assertThrows(IllegalArgumentException.class, () -> service.decide(5L, true, null, admin));
    }

    @Test void declinedRequestKeepsTimesheetClosedAndNotifiesEmployee() {
        var request = TimesheetCorrectionRequest.builder().id(5L).timesheet(sheet).project(project).user(employee)
                .status(TimesheetCorrectionStatus.PENDING).employeeComment("Correction needed").build();
        when(requests.findById(5L)).thenReturn(Optional.of(request));
        when(requests.findForUpdate(5L)).thenReturn(Optional.of(request));
        assertEquals(TimesheetCorrectionStatus.DECLINED, service.decide(5L, false, "Payroll finalized", admin).status());
        verify(timesheetService, never()).openAfterApprovedRequest(anyLong(), anyLong(), anyString(), anyLong());
        verify(notifications).createNotification(eq(1L), eq("TIMESHEET_CORRECTION_DECLINED"), anyString(), contains("Payroll finalized"), eq(5L), eq("TimesheetCorrectionRequest"));
    }

    @Test void currentApprovedMonthCanRequestOpening() {
        YearMonth current = YearMonth.now();
        sheet.setYear(current.getYear());
        sheet.setMonth(current.getMonthValue());
        sheet.setStatus(TimesheetStatus.APPROVED);
        sheet.setApprovalFrozen(true);
        var assignment = assignments.findByProjectIdAndUserId(3L, 1L).orElseThrow();
        assignment.setStartDate(current.atDay(1));
        assignment.setEndDate(current.atEndOfMonth());
        assertEquals(TimesheetCorrectionStatus.PENDING,
                service.request(4L, 3L, "Correct an approved entry", employee).status());
    }

    @Test void currentDraftMonthNeedsNoOpeningAndSystemAdminCannotDecide() {
        YearMonth current = YearMonth.now();
        sheet.setYear(current.getYear());
        sheet.setMonth(current.getMonthValue());
        var assignment = assignments.findByProjectIdAndUserId(3L, 1L).orElseThrow();
        assignment.setStartDate(current.atDay(1));
        assignment.setEndDate(current.atEndOfMonth());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.request(4L, 3L, "Edit current hours", employee)).getMessage()
                .contains("still open"));
        admin.setRole(UserRole.ADMIN);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.pending(admin));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.decide(5L, true, "Approved", admin));
    }
}
