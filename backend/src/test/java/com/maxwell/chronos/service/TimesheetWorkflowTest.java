package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.dto.TimeEntrySessionDTO;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimesheetWorkflowTest {
    @Mock TimesheetRepository timesheets;
    @Mock TimeEntryRepository entries;
    @Mock TimesheetProjectSubmissionRepository submissions;
    @Mock UserRepository users;
    @Mock ProjectRepository projects;
    @Mock ProjectAssignmentRepository assignments;
    @Mock ProjectHourPlanRepository plans;
    @Mock VacationRequestRepository vacations;
    @Mock AuditService audit;
    @Mock NotificationService notifications;
    @Mock ProjectService projectService;
    @InjectMocks TimesheetService service;
    User employee, admin;
    Project project;
    Timesheet sheet;
    TimesheetProjectSubmission submission;

    @BeforeEach void setup() {
        employee = User.builder().id(1L).firstName("Test").lastName("Employee").role(UserRole.EMPLOYEE)
                .isActive(true).hourlyRate(BigDecimal.TEN).build();
        admin = User.builder().id(2L).role(UserRole.SUPER_ADMIN).isActive(true).build();
        project = Project.builder().id(3L).code("P1").name("Project").status(ProjectStatus.ACTIVE).build();
        sheet = Timesheet.builder().id(4L).user(employee).year(2026).month(9).status(TimesheetStatus.DRAFT).build();
        submission = TimesheetProjectSubmission.builder().id(5L).timesheet(sheet).project(project)
                .status(TimesheetStatus.DRAFT).totalHours(BigDecimal.ZERO).build();
        when(timesheets.findForUpdate(4L)).thenReturn(Optional.of(sheet));
        when(timesheets.save(any())).thenAnswer(i -> i.getArgument(0));
        when(submissions.findTimesheetId(5L)).thenReturn(Optional.of(4L));
        when(submissions.findById(5L)).thenReturn(Optional.of(submission));
        when(submissions.findByTimesheetIdAndProjectId(4L, 3L)).thenReturn(Optional.of(submission));
        when(submissions.findByTimesheetId(4L)).thenReturn(List.of(submission));
        when(submissions.save(any())).thenAnswer(i -> i.getArgument(0));
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(projects.findById(3L)).thenReturn(Optional.of(project));
        when(projectService.isAssigned(3L, 1L)).thenReturn(true);
        when(assignments.findByProjectIdAndUserId(3L, 1L)).thenReturn(Optional.of(ProjectAssignment.builder()
                .project(project).user(employee).plannedHours(new BigDecimal("160")).billRate(new BigDecimal("75"))
                .startDate(LocalDate.of(2026,9,5)).endDate(LocalDate.of(2026,9,25)).isActive(true).build()));
        when(entries.save(any())).thenAnswer(i -> { TimeEntry e = i.getArgument(0); e.setId(9L); return e; });
    }

    private void add(LocalDate date, String hours, List<TimeEntrySessionDTO> sessions) {
        service.addTimeEntry(4L, date, new BigDecimal(hours), "", 3L, sessions, 1L);
    }

    @Test void superAdminCannotSubmitMonthlyOrProjectTimesheets() {
        employee.setRole(UserRole.SUPER_ADMIN);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submitTimesheet(4L, 1L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submitProjectTimesheet(4L, 3L, 1L));
        verify(submissions, never()).save(any());
        assertEquals(TimesheetStatus.DRAFT, sheet.getStatus());
    }

    @Test void employeeManagerCanReadAndApproveButUnrelatedEmployeeCannot() {
        add(LocalDate.of(2026,9,10), "8", List.of());
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(users.findById(6L)).thenReturn(Optional.of(manager));
        when(projectService.managesProject(3L, 6L)).thenReturn(true);
        assertEquals(4L, service.getTimesheetById(4L, 6L, false).getId());
        submission.setStatus(TimesheetStatus.SUBMITTED);
        var stranger = User.builder().id(7L).role(UserRole.EMPLOYEE).build();
        when(users.findById(7L)).thenReturn(Optional.of(stranger));
        assertThrows(IllegalArgumentException.class, () -> service.approveProjectSubmission(5L, 7L));
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 6L).getStatus());
    }

    @Test void managersOwnHoursRequireDesignatedApproverAndNeverSelfApproval() {
        project.setProjectManager(employee);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        assertThrows(IllegalArgumentException.class, () -> service.approveProjectSubmission(5L, 1L));
        var approver = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManagerHoursApprover(approver);
        when(users.findById(6L)).thenReturn(Optional.of(approver));
        when(projectService.canApproveProjectManagerHours(3L, 6L)).thenReturn(true);
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 6L).getStatus());
    }

    @Test void rejectsWrongMonthAndDatesOutsideAssignment() {
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026,10,10), "8", List.of()));
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026,9,1), "8", List.of()));
        verify(entries, never()).save(any());
    }

    @Test void rejectsDailyHoursAcrossProjects() {
        sheet.getTimeEntries().add(TimeEntry.builder().id(8L).timesheet(sheet).entryDate(LocalDate.of(2026,9,10))
                .hours(new BigDecimal("20")).project(Project.builder().id(10L).build()).build());
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026,9,10), "8", List.of()));
    }

    @Test void rejectsMismatchedAndOverlappingSessions() {
        var session = TimeEntrySessionDTO.builder().loginTime(LocalTime.of(9,0)).logoutTime(LocalTime.of(10,0)).build();
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026,9,10), "8", List.of(session)));
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026,9,10), "2", List.of(session, session)));
    }

    @Test void acceptsValidSessionAndSynchronizesTotals() {
        var session = TimeEntrySessionDTO.builder().loginTime(LocalTime.of(9,0)).logoutTime(LocalTime.of(10,30)).build();
        add(LocalDate.of(2026,9,10), "1.50", List.of(session));
        assertEquals(0, new BigDecimal("1.50").compareTo(sheet.getTotalHours()));
        assertEquals(sheet.getTotalHours(), submission.getTotalHours());
    }

    @Test void reopenClearsProjectApprovalAndAllowsEditing() {
        sheet.setStatus(TimesheetStatus.APPROVED);
        submission.setStatus(TimesheetStatus.APPROVED);
        submission.setApprovedBillRate(new BigDecimal("75"));
        service.reopenTimesheet(4L, "Correct hours", 2L);
        assertTrue(submission.isEditable());
        assertNull(submission.getApprovedBillRate());
        add(LocalDate.of(2026,9,10), "8", List.of());
        assertEquals(new BigDecimal("8"), submission.getTotalHours());
    }

    @Test void monthlyApprovalSnapshotsProjectRateAndBlocksFurtherEdits() {
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        sheet.getTimeEntries().add(TimeEntry.builder().id(8L).timesheet(sheet).entryDate(LocalDate.of(2026,9,10))
                .hours(new BigDecimal("8")).project(project).build());
        service.approveTimesheet(4L, 2L);
        assertEquals(TimesheetStatus.APPROVED, submission.getStatus());
        assertEquals(TimesheetStatus.APPROVED, sheet.getStatus());
        assertEquals(new BigDecimal("75"), submission.getApprovedBillRate());
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026,9,11), "8", List.of()));
    }

    @Test void frozenProjectsRejectEntryAndSubmission() {
        for (ProjectStatus status : List.of(ProjectStatus.ON_HOLD, ProjectStatus.ARCHIVED, ProjectStatus.COMPLETED)) {
            project.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026, 9, 10), "8", List.of()));
            assertThrows(IllegalArgumentException.class, () -> service.submitProjectTimesheet(4L, 3L, 1L));
        }
        verify(entries, never()).save(any());
    }

    @Test void futureMonthCannotBeSubmitted() {
        YearMonth future = YearMonth.now().plusMonths(1);
        sheet.setYear(future.getYear());
        sheet.setMonth(future.getMonthValue());
        assertThrows(IllegalArgumentException.class, () -> service.submitProjectTimesheet(4L, 3L, 1L));
        verify(submissions, never()).save(any());
    }

    @Test void approvedMonthDoesNotFreezeAnotherDraftProject() {
        sheet.setStatus(TimesheetStatus.APPROVED);
        submission.setStatus(TimesheetStatus.DRAFT);
        add(LocalDate.of(2026, 9, 10), "8", List.of());
        verify(entries).save(any());
    }

    @Test void approvedProjectStillRejectsChanges() {
        submission.setStatus(TimesheetStatus.APPROVED);
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026, 9, 10), "8", List.of()));
        verify(entries, never()).save(any());
    }

    @Test void lockedMonthStillRejectsDraftChanges() {
        sheet.setStatus(TimesheetStatus.LOCKED);
        assertThrows(IllegalArgumentException.class, () -> add(LocalDate.of(2026, 9, 10), "8", List.of()));
        verify(entries, never()).save(any());
    }
}
