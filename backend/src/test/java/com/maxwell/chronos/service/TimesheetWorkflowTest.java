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
                .isActive(true).build();
        admin = User.builder().id(2L).role(UserRole.ADMIN).isActive(true).build();
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

    @Test void cumulativeHoursCombineOutstandingEntriesAndApprovedTotals() {
        when(entries.sumLoggedHoursToDate(eq(1L), eq(3L), eq(LocalDate.now()),
                eq(List.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED))))
                .thenReturn(new BigDecimal("12"));
        var approved = TimesheetProjectSubmission.builder().status(TimesheetStatus.APPROVED)
                .totalHours(new BigDecimal("80")).approvedAt(LocalDateTime.now().minusDays(1)).build();
        var locked = TimesheetProjectSubmission.builder().status(TimesheetStatus.LOCKED)
                .totalHours(new BigDecimal("40")).build();
        submission.setTotalHours(new BigDecimal("12"));
        when(submissions.findByProjectIdAndTimesheetUserId(3L, 1L)).thenReturn(List.of(approved, locked, submission));
        assertEquals(new BigDecimal("132"), service.getProjectSubmission(4L, 3L, employee).getLoggedHoursToDate());
    }

    @Test void missingSubmissionsIncludeAbsentTimesheetsAndRespectManagerScope() {
        project.setIsActive(true);
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(projectService.canManageProjects(6L)).thenReturn(true);
        when(projectService.visibleProjectIds(manager)).thenReturn(Set.of(3L));
        when(projectService.visibleProjectIds(admin)).thenReturn(Set.of(3L));
        var assignment = assignments.findByProjectIdAndUserId(3L, 1L).orElseThrow();
        when(assignments.findAll()).thenReturn(List.of(assignment));
        var missing = service.getMissingTimesheets(2026, 9, manager);
        assertEquals(1, missing.size());
        assertEquals("NOT_STARTED", missing.get(0).status());
        assertNull(missing.get(0).timesheetId());
        when(projectService.visibleProjectIds(manager)).thenReturn(Set.of());
        assertTrue(service.getMissingTimesheets(2026, 9, manager).isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getMissingTimesheets(2026, 9, employee));
        assertTrue(service.getMissingTimesheets(2026, 10, admin).isEmpty());
        when(timesheets.findByYearAndMonth(2026,9)).thenReturn(List.of(sheet));
        submission.setStatus(TimesheetStatus.SUBMITTED);
        assertEquals("SUBMITTED", service.getMissingTimesheets(2026, 9, admin).get(0).status());
        submission.setStatus(TimesheetStatus.REJECTED);
        assertEquals("REJECTED", service.getMissingTimesheets(2026, 9, admin).get(0).status());
        for (var status : List.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED, TimesheetStatus.CHANGE_REQUESTED)) {
            submission.setStatus(status);
            assertEquals(status.name(), service.getMissingTimesheets(2026, 9, admin).get(0).status());
        }
        project.setStatus(ProjectStatus.COMPLETED);
        assertTrue(service.getMissingTimesheets(2026, 9, admin).isEmpty());
        project.setStatus(ProjectStatus.ARCHIVED);
        assertTrue(service.getMissingTimesheets(2026, 9, admin).isEmpty());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setIsActive(false);
        assertTrue(service.getMissingTimesheets(2026, 9, admin).isEmpty());
    }

    @Test void rejectedTimesheetCanBeCorrectedAndResubmitted() {
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(users.findById(6L)).thenReturn(Optional.of(manager));
        when(projectService.managesProject(3L,6L)).thenReturn(true);
        add(LocalDate.of(2026,9,10), "8", List.of());
        service.submitProjectTimesheet(4L,3L,1L);
        service.rejectProjectSubmission(5L,"Correct Thursday hours",6L);
        assertEquals("Correct Thursday hours", submission.getRejectionReason());
        when(entries.findById(9L)).thenReturn(sheet.getTimeEntries().stream().findFirst());
        service.updateTimeEntry(4L, 9L, new BigDecimal("6"), "Corrected", 3L, List.of(), 1L);
        assertEquals(TimesheetStatus.SUBMITTED, service.submitProjectTimesheet(4L,3L,1L).getStatus());
        assertNull(submission.getRejectionReason());
    }

    @Test void approvalHistoryPreservesRejectionsAndResubmissionsAndRejectsStrangers() {
        when(timesheets.findById(4L)).thenReturn(Optional.of(sheet));
        var rejected = com.maxwell.chronos.dto.AuditLogDTO.builder().id(10L).action(AuditAction.TIMESHEET_REJECTED).createdAt(LocalDateTime.of(2026,9,10,12,0)).build();
        var resubmitted = com.maxwell.chronos.dto.AuditLogDTO.builder().id(11L).action(AuditAction.TIMESHEET_SUBMITTED).createdAt(LocalDateTime.of(2026,9,11,12,0)).build();
        var reopened = com.maxwell.chronos.dto.AuditLogDTO.builder().id(12L).action(AuditAction.TIMESHEET_REOPENED).createdAt(LocalDateTime.of(2026,9,12,12,0)).build();
        when(audit.getAuditLogsByEntityTypeAndId("TimesheetProjectSubmission",5L)).thenReturn(List.of(rejected,resubmitted));
        when(audit.getAuditLogsByEntityTypeAndId("Timesheet",4L)).thenReturn(List.of(reopened));
        assertEquals(List.of(reopened,resubmitted,rejected),service.getApprovalHistory(4L,3L,employee));
        var stranger = User.builder().id(90L).role(UserRole.EMPLOYEE).build();
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getApprovalHistory(4L,3L,stranger));
    }

    private void add(LocalDate date, String hours, List<TimeEntrySessionDTO> sessions) {
        service.addTimeEntry(4L, date, new BigDecimal(hours), "", 3L, sessions, 1L);
    }

    @Test void systemAdminCannotSubmitMonthlyOrProjectTimesheets() {
        employee.setRole(UserRole.ADMIN);
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

    @Test void projectDetailExcludesOtherProjectsAndRejectsUnrelatedProjectIds() {
        add(LocalDate.of(2026,9,10), "8", List.of());
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        when(projectService.canReviewProjects(6L)).thenReturn(true);
        when(projectService.visibleProjectIds(manager)).thenReturn(Set.of(3L));
        var other = Project.builder().id(30L).code("PRIVATE").build();
        when(projects.findById(30L)).thenReturn(Optional.of(other));
        sheet.getTimeEntries().add(TimeEntry.builder().id(31L).timesheet(sheet).project(other).hours(new BigDecimal("20")).build());
        var detail = service.getProjectTimesheet(4L, 3L, manager);
        assertEquals(1, detail.getTimeEntries().size());
        assertEquals(new BigDecimal("8"), detail.getTotalHours());
        assertEquals(3L, detail.getPrimaryProjectId());
        assertTrue(detail.getVacationDays().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getProjectTimesheet(4L, 30L, manager));
    }

    @Test void managersOwnHoursRouteToDesignatedApprover() {
        project.setProjectManager(employee);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        var approver = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManagerHoursApprover(approver);
        when(users.findById(6L)).thenReturn(Optional.of(approver));
        when(projectService.canApproveProjectManagerHours(3L, 6L)).thenReturn(true);
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 6L).getStatus());
    }

    @Test void managerCanApproveOwnHoursWhenDesignatedAndSeeThemInQueue() {
        project.setProjectManager(employee);
        project.setProjectManagerHoursApprover(employee);
        submission.setAssignedApprover(employee);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(projectService.canReviewProjects(1L)).thenReturn(true);
        when(submissions.findByStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of(submission));
        assertEquals(1, service.getPendingProjectSubmissions(employee).size());
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 1L).getStatus());
        assertEquals(employee, submission.getApprovedBy());
    }

    @Test void pmOwnHoursAppearInQueueEvenWhenAssignedToAnotherApprover() {
        project.setProjectManager(employee);
        project.setProjectManagerHoursApprover(admin);
        submission.setAssignedApprover(admin);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(projectService.canReviewProjects(1L)).thenReturn(true);
        when(submissions.findByStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of(submission));
        assertEquals(1, service.getPendingProjectSubmissions(employee).size());
        assertEquals(1L, service.getPendingProjectSubmissions(employee).get(0).getProjectManagerId());
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 1L).getStatus());
        assertEquals(employee, submission.getApprovedBy());
    }

    @Test void approverWithoutPmAssignmentCannotAccessMissingTimesheets() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getMissingTimesheets(2026, 9, employee));
    }

    @Test void storedApproverControlsReviewUntilExplicitTransfer() {
        var original = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        var replacement = User.builder().id(7L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(replacement);
        submission.setAssignedApprover(original);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(6L)).thenReturn(Optional.of(original));
        when(users.findById(7L)).thenReturn(Optional.of(replacement));
        assertThrows(IllegalArgumentException.class, () -> service.approveProjectSubmission(5L, 7L));
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

    @Test void systemAdminCannotReviewEvenWhenAssignedAsManager() {
        submission.setStatus(TimesheetStatus.SUBMITTED);
        project.setProjectManager(admin);
        when(projectService.managesProject(3L, 2L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.approveProjectSubmission(5L, 2L));
        assertThrows(IllegalArgumentException.class, () -> service.approveTimesheet(4L, 2L));
        assertThrows(IllegalArgumentException.class, () -> service.rejectProjectSubmission(5L, "Correction", 2L));
        assertThrows(IllegalArgumentException.class, () -> service.rejectTimesheet(4L, "Correction", 2L));
        verify(submissions, never()).save(any());
        assertEquals(TimesheetStatus.SUBMITTED, submission.getStatus());
    }

    @Test void monthlyApprovalSnapshotsProjectRateAndBlocksFurtherEdits() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        project.setProjectManager(admin);
        when(projectService.managesProject(3L, 2L)).thenReturn(true);
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
