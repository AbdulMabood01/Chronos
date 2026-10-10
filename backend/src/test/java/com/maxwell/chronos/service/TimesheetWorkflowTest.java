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
    @Mock CompanyAccessService access;
    @Mock TimesheetPeriodService periods;
    @InjectMocks TimesheetService service;
    final YearMonth workMonth = YearMonth.now();
    User employee, admin;
    Project project;
    Timesheet sheet;
    TimesheetProjectSubmission submission;

    @BeforeEach void setup() {
        lenient().when(access.companyIds(anyLong())).thenReturn(List.of(1L));
        lenient().when(access.maySubmit(anyLong(), anyLong())).thenReturn(true);
        lenient().when(access.mayReview(anyLong(), anyLong(), anyLong(), anyBoolean(), nullable(String.class)))
                .thenAnswer(call -> canReview(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        lenient().doAnswer(call -> {
            if (!canReview(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                throw new org.springframework.security.access.AccessDeniedException("A separate project reviewer is required");
            return null;
        }).when(access).requireMayReview(anyLong(), anyLong(), anyLong(), anyBoolean(), nullable(String.class));
        lenient().when(access.hasPlatformRole(2L, "PLATFORM_ADMIN"))
                .thenAnswer(call -> admin.getRole() == UserRole.ADMIN);
        lenient().when(access.mayManageProject(anyLong(), anyLong())).thenAnswer(call ->
                ((Long) call.getArgument(1)).equals(2L) && admin.getRole() == UserRole.PROJECT_ADMIN);
        employee = User.builder().id(1L).firstName("Test").lastName("Employee").role(UserRole.EMPLOYEE)
                .isActive(true).build();
        admin = User.builder().id(2L).role(UserRole.ADMIN).isActive(true).build();
        project = Project.builder().id(3L).companyId(1L).code("P1").name("Project").status(ProjectStatus.ACTIVE).build();
        sheet = Timesheet.builder().id(4L).companyId(1L).user(employee).year(workMonth.getYear()).month(workMonth.getMonthValue()).status(TimesheetStatus.DRAFT).build();
        submission = TimesheetProjectSubmission.builder().id(5L).timesheet(sheet).project(project)
                .status(TimesheetStatus.DRAFT).totalHours(BigDecimal.ZERO).build();
        lenient().doAnswer(call -> {
            Long projectId = call.getArgument(1);
            var current = submissions.findByTimesheetIdAndProjectId(4L, projectId).orElse(null);
            boolean open = current != null && current.isCorrectionOpen() && current.isEditable();
            if (!open && (sheet.isApprovalFrozen() || sheet.getStatus() == TimesheetStatus.APPROVED
                    || sheet.isLocked() || current != null && (current.getStatus() == TimesheetStatus.APPROVED
                    || current.getStatus() == TimesheetStatus.LOCKED)))
                throw new IllegalArgumentException("This timesheet was approved; request an opening from your Project Admin");
            if (!open && !TimesheetService.standardEditingOpen(sheet, LocalDate.now()))
                throw new IllegalArgumentException("This timesheet month is closed; request an opening from your Project Admin");
            return null;
        }).when(periods).requireEditable(anyLong(), anyLong(), any(LocalDate.class));
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
                .startDate(workMonth.atDay(5)).endDate(workMonth.atDay(25)).isActive(true).build()));
        when(entries.save(any())).thenAnswer(i -> { TimeEntry e = i.getArgument(0); e.setId(9L); return e; });
    }

    private boolean canReview(Long projectId, Long reviewerId, Long employeeId) {
        if (!projectId.equals(3L)) return false;
        if (reviewerId.equals(employeeId) || reviewerId.equals(2L) && admin.getRole() == UserRole.ADMIN) return false;
        return reviewerId.equals(2L) && admin.getRole() == UserRole.PROJECT_ADMIN
                || project.getProjectManager() != null && reviewerId.equals(project.getProjectManager().getId())
                || project.getProjectManagerHoursApprover() != null && reviewerId.equals(project.getProjectManagerHoursApprover().getId());
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
        when(periods.period(eq(3L), any(LocalDate.class))).thenAnswer(call -> {
            LocalDate day = call.getArgument(1);
            YearMonth month = YearMonth.from(day);
            return new TimesheetPeriodService.Period(month.atDay(1), month.atEndOfMonth(), "MONTHLY");
        });
        when(periods.view(eq(1L), eq(1L), eq(3L), any(LocalDate.class))).thenAnswer(call -> {
            var state = new HashMap<String, Object>();
            state.put("requiresSubmission", true);
            state.put("status", submission.getStatus().name());
            state.put("totalHours", BigDecimal.ZERO);
            state.put("late", true);
            state.put("timesheetId", null);
            return state;
        });
        when(projectService.canManageProjects(6L)).thenReturn(true);
        when(projectService.getProjects(manager)).thenReturn(List.of(com.maxwell.chronos.dto.ProjectDTO.builder().id(3L).build()));
        var assignment = assignments.findByProjectIdAndUserId(3L, 1L).orElseThrow();
        when(assignments.findAll()).thenReturn(List.of(assignment));
        var missing = service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), manager);
        assertEquals(1, missing.size());
        assertEquals("NOT_STARTED", missing.get(0).status());
        assertNull(missing.get(0).timesheetId());
        when(projectService.getProjects(manager)).thenReturn(List.of());
        assertTrue(service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), manager).isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), employee));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(),admin));
        admin.setRole(UserRole.PROJECT_ADMIN);
        when(projectService.canManageProjects(2L)).thenReturn(true);
        when(projectService.getProjects(admin)).thenReturn(List.of(com.maxwell.chronos.dto.ProjectDTO.builder().id(3L).build()));
        assertTrue(service.getMissingTimesheets(workMonth.plusMonths(1).getYear(), workMonth.plusMonths(1).getMonthValue(), admin).isEmpty());
        when(timesheets.findByYearAndMonth(workMonth.getYear(), workMonth.getMonthValue())).thenReturn(List.of(sheet));
        submission.setStatus(TimesheetStatus.SUBMITTED);
        assertEquals("SUBMITTED", service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), admin).get(0).status());
        submission.setStatus(TimesheetStatus.REJECTED);
        assertEquals("REJECTED", service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), admin).get(0).status());
        for (var status : List.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED, TimesheetStatus.CHANGE_REQUESTED)) {
            submission.setStatus(status);
            assertEquals(status.name(), service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), admin).get(0).status());
        }
        project.setStatus(ProjectStatus.COMPLETED);
        assertTrue(service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), admin).isEmpty());
        project.setStatus(ProjectStatus.ARCHIVED);
        assertTrue(service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), admin).isEmpty());
        project.setStatus(ProjectStatus.ACTIVE);
        project.setIsActive(false);
        assertTrue(service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), admin).isEmpty());
    }

    @Test void rejectedTimesheetCanBeCorrectedAndResubmitted() {
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(users.findById(6L)).thenReturn(Optional.of(manager));
        when(projectService.managesProject(3L,6L)).thenReturn(true);
        add(workMonth.atDay(10), "8", List.of());
        service.submitProjectTimesheet(4L,3L,1L);
        service.rejectProjectSubmission(5L,"Correct Thursday hours",6L);
        assertEquals("Correct Thursday hours", submission.getRejectionReason());
        when(entries.findById(9L)).thenReturn(sheet.getTimeEntries().stream().findFirst());
        service.updateTimeEntry(4L, 9L, new BigDecimal("6"), "Corrected", 3L, List.of(), 1L);
        assertEquals(TimesheetStatus.SUBMITTED, service.submitProjectTimesheet(4L,3L,1L).getStatus());
        assertNull(submission.getRejectionReason());
    }

    @Test void rejectedPriorMonthCanBeCorrectedAfterOffboardingAndResubmitted() {
        YearMonth previous = YearMonth.now().minusMonths(1);
        LocalDate workDay = previous.atDay(10);
        sheet.setYear(previous.getYear());
        sheet.setMonth(previous.getMonthValue());
        var assignment = ProjectAssignment.builder().project(project).user(employee)
                .plannedHours(new BigDecimal("160")).startDate(previous.atDay(1))
                .endDate(previous.atEndOfMonth()).isActive(false).build();
        when(assignments.findByProjectIdAndUserId(3L, 1L)).thenReturn(Optional.of(assignment));
        when(projectService.isAssigned(3L, 1L)).thenReturn(false);
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(users.findById(6L)).thenReturn(Optional.of(manager));
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(projectService.managesProject(3L, 6L)).thenReturn(true);

        service.rejectProjectSubmission(5L, "Correct hours", 6L);
        assertTrue(submission.isCorrectionOpen());
        assertNull(submission.getCorrectionUntil());
        service.addTimeEntry(4L, workDay, new BigDecimal("8"), "Corrected", 3L, List.of(), 1L);
        assertEquals(TimesheetStatus.SUBMITTED, service.submitProjectTimesheet(4L, 3L, 1L).getStatus());
        assertNull(submission.getCorrectionUntil());
        assertFalse(submission.isCorrectionOpen());
    }

    @Test void approvalHistoryPreservesRejectionsAndResubmissionsAndRejectsStrangers() {
        when(timesheets.findById(4L)).thenReturn(Optional.of(sheet));
        var rejected = com.maxwell.chronos.dto.AuditLogDTO.builder().id(10L).action(AuditAction.TIMESHEET_REJECTED).createdAt(LocalDateTime.of(workMonth.getYear(), workMonth.getMonthValue(),10,12,0)).build();
        var resubmitted = com.maxwell.chronos.dto.AuditLogDTO.builder().id(11L).action(AuditAction.TIMESHEET_SUBMITTED).createdAt(LocalDateTime.of(workMonth.getYear(), workMonth.getMonthValue(),11,12,0)).build();
        var reopened = com.maxwell.chronos.dto.AuditLogDTO.builder().id(12L).action(AuditAction.TIMESHEET_REOPENED).createdAt(LocalDateTime.of(workMonth.getYear(), workMonth.getMonthValue(),12,12,0)).build();
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
        employee.setRole(UserRole.ADMIN);when(access.hasPlatformRole(1L,"PLATFORM_ADMIN")).thenReturn(true);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submitTimesheet(4L, 1L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submitProjectTimesheet(4L, 3L, 1L));
        verify(submissions, never()).save(any());
        assertEquals(TimesheetStatus.DRAFT, sheet.getStatus());
    }

    @Test void employeeManagerCanReadAndApproveButUnrelatedEmployeeCannot() {
        add(workMonth.atDay(10), "8", List.of());
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(users.findById(6L)).thenReturn(Optional.of(manager));
        when(projectService.managesProject(3L, 6L)).thenReturn(true);
        assertEquals(4L, service.getTimesheetById(4L, 6L, false).getId());
        submission.setStatus(TimesheetStatus.SUBMITTED);
        var stranger = User.builder().id(7L).role(UserRole.EMPLOYEE).build();
        when(users.findById(7L)).thenReturn(Optional.of(stranger));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.approveProjectSubmission(5L, 7L));
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 6L).getStatus());
    }

    @Test void projectDetailExcludesOtherProjectsAndRejectsUnrelatedProjectIds() {
        add(workMonth.atDay(10), "8", List.of());
        var manager = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(manager);
        when(projectService.canReviewProjects(6L)).thenReturn(true);
        when(projectService.visibleProjectIds(manager)).thenReturn(Set.of(3L));
        var other = Project.builder().id(30L).companyId(1L).code("PRIVATE").build();
        when(projects.findById(30L)).thenReturn(Optional.of(other));
        sheet.getTimeEntries().add(TimeEntry.builder().id(31L).timesheet(sheet).project(other).hours(new BigDecimal("20")).build());
        var detail = service.getProjectTimesheet(4L, 3L, manager);
        assertEquals(1, detail.getTimeEntries().size());
        assertEquals(new BigDecimal("8"), detail.getTotalHours());
        assertEquals(3L, detail.getPrimaryProjectId());
        assertTrue(detail.getVacationDays().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.getProjectTimesheet(4L, 30L, manager));
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

    @Test void managerCannotApproveOwnHoursEvenWhenDesignated() {
        project.setProjectManager(employee);
        project.setProjectManagerHoursApprover(employee);
        submission.setAssignedApprover(employee);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(projectService.canReviewProjects(1L)).thenReturn(true);
        when(submissions.findByStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of(submission));
        assertTrue(service.getPendingProjectSubmissions(employee).isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.approveProjectSubmission(5L, 1L));
    }

    @Test void pmOwnHoursStayOutOfTheirReviewQueue() {
        project.setProjectManager(employee);
        project.setProjectManagerHoursApprover(admin);
        submission.setAssignedApprover(admin);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        when(projectService.canReviewProjects(1L)).thenReturn(true);
        when(submissions.findByStatus(TimesheetStatus.SUBMITTED)).thenReturn(List.of(submission));
        assertTrue(service.getPendingProjectSubmissions(employee).isEmpty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.approveProjectSubmission(5L, 1L));
    }

    @Test void approverWithoutPmAssignmentCannotAccessMissingTimesheets() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.getMissingTimesheets(workMonth.getYear(), workMonth.getMonthValue(), employee));
    }

    @Test void currentManagerControlsReviewAfterHandover() {
        var original = User.builder().id(6L).role(UserRole.EMPLOYEE).build();
        var replacement = User.builder().id(7L).role(UserRole.EMPLOYEE).build();
        project.setProjectManager(replacement);
        submission.setAssignedApprover(original);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        when(users.findById(6L)).thenReturn(Optional.of(original));
        when(users.findById(7L)).thenReturn(Optional.of(replacement));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.approveProjectSubmission(5L, 6L));
        assertEquals(TimesheetStatus.APPROVED, service.approveProjectSubmission(5L, 7L).getStatus());
    }

    @Test void rejectsWrongMonthAndDatesOutsideAssignment() {
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.plusMonths(1).atDay(10), "8", List.of()));
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(1), "8", List.of()));
        verify(entries, never()).save(any());
    }

    @Test void rejectsDailyHoursAcrossProjects() {
        sheet.getTimeEntries().add(TimeEntry.builder().id(8L).timesheet(sheet).entryDate(workMonth.atDay(10))
                .hours(new BigDecimal("20")).project(Project.builder().id(10L).build()).build());
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "8", List.of()));
    }

    @Test void rejectsMismatchedAndOverlappingSessions() {
        var session = TimeEntrySessionDTO.builder().loginTime(LocalTime.of(9,0)).logoutTime(LocalTime.of(10,0)).build();
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "8", List.of(session)));
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "2", List.of(session, session)));
    }

    @Test void acceptsValidSessionAndSynchronizesTotals() {
        var session = TimeEntrySessionDTO.builder().loginTime(LocalTime.of(9,0)).logoutTime(LocalTime.of(10,30)).build();
        add(workMonth.atDay(10), "1.50", List.of(session));
        assertEquals(0, new BigDecimal("1.50").compareTo(sheet.getTotalHours()));
        assertEquals(sheet.getTotalHours(), submission.getTotalHours());
    }

    @Test void approvedOpeningClearsProjectApprovalAndStaysOpenUntilResubmission() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        sheet.setStatus(TimesheetStatus.APPROVED);
        sheet.setApprovalFrozen(true);
        submission.setStatus(TimesheetStatus.APPROVED);
        submission.setApprovedBillRate(new BigDecimal("75"));
        var opened = service.openAfterApprovedRequest(4L, 3L, "Correct hours", 2L);
        assertTrue(submission.isEditable());
        assertTrue(opened.getOpeningActive());
        assertNull(submission.getApprovedBillRate());
        assertTrue(sheet.isApprovalFrozen());
        assertTrue(submission.isCorrectionOpen());
        assertNull(submission.getCorrectionUntil());
        add(workMonth.atDay(10), "8", List.of());
        assertEquals(new BigDecimal("8"), submission.getTotalHours());
        var resubmitted = service.submitProjectTimesheet(4L, 3L, 1L);
        assertEquals(TimesheetStatus.SUBMITTED, resubmitted.getStatus());
        assertFalse(resubmitted.getOpeningActive());
        assertTrue(sheet.isApprovalFrozen());
    }

    @Test void systemAdminCannotReviewEvenWhenAssignedAsManager() {
        submission.setStatus(TimesheetStatus.SUBMITTED);
        project.setProjectManager(admin);
        when(projectService.managesProject(3L, 2L)).thenReturn(true);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.approveProjectSubmission(5L, 2L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.approveTimesheet(4L, 2L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.rejectProjectSubmission(5L, "Correction", 2L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.rejectTimesheet(4L, "Correction", 2L));
        verify(submissions, never()).save(any());
        assertEquals(TimesheetStatus.SUBMITTED, submission.getStatus());
    }

    @Test void monthlyApprovalSnapshotsProjectRateAndBlocksFurtherEdits() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        project.setProjectManager(admin);
        when(projectService.managesProject(3L, 2L)).thenReturn(true);
        sheet.setStatus(TimesheetStatus.SUBMITTED);
        submission.setStatus(TimesheetStatus.SUBMITTED);
        sheet.getTimeEntries().add(TimeEntry.builder().id(8L).timesheet(sheet).entryDate(workMonth.atDay(10))
                .hours(new BigDecimal("8")).project(project).build());
        service.approveTimesheet(4L, 2L);
        assertEquals(TimesheetStatus.APPROVED, submission.getStatus());
        assertEquals(TimesheetStatus.APPROVED, sheet.getStatus());
        assertTrue(sheet.isApprovalFrozen());
        assertEquals(new BigDecimal("75"), submission.getApprovedBillRate());
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(11), "8", List.of()));
    }

    @Test void frozenProjectsRejectEntryAndSubmission() {
        for (ProjectStatus status : List.of(ProjectStatus.ON_HOLD, ProjectStatus.ARCHIVED, ProjectStatus.COMPLETED)) {
            project.setStatus(status);
            assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "8", List.of()));
            assertThrows(IllegalArgumentException.class, () -> service.submitProjectTimesheet(4L, 3L, 1L));
        }
        verify(entries, never()).save(any());
    }

    @Test void pastDraftAllowsFirstHourEntryWithoutReopening() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        YearMonth past = YearMonth.now().minusMonths(2);
        sheet.setYear(past.getYear());
        sheet.setMonth(past.getMonthValue());
        var assignment = assignments.findByProjectIdAndUserId(3L, 1L).orElseThrow();
        assignment.setStartDate(past.atDay(1));
        assignment.setEndDate(past.atEndOfMonth());
        when(submissions.findByTimesheetIdAndProjectId(4L, 3L)).thenReturn(Optional.empty());
        when(submissions.save(any())).thenAnswer(i -> {
            TimesheetProjectSubmission saved = i.getArgument(0);
            saved.setId(5L);
            when(submissions.findByTimesheetIdAndProjectId(4L, 3L)).thenReturn(Optional.of(saved));
            return saved;
        });

        add(past.atDay(10), "8", List.of());
        assertEquals(new BigDecimal("8"), service.submitProjectTimesheet(4L, 3L, 1L).getTotalHours());
    }

    @Test void projectAdminCannotOpenPastDraftOutsideAssignmentDates() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        YearMonth past = YearMonth.now().minusMonths(2);
        sheet.setYear(past.getYear());
        sheet.setMonth(past.getMonthValue());
        assertEquals("Project assignment did not cover this timesheet month",
                assertThrows(IllegalArgumentException.class,
                        () -> service.openAfterApprovedRequest(4L, 3L, "Late entry approved", 2L)).getMessage());
    }

    @Test void projectAdminCanOpenOnlyTheRequestedProjectFromLockedMonth() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        YearMonth past = YearMonth.now().minusMonths(1);
        sheet.setYear(past.getYear());
        sheet.setMonth(past.getMonthValue());
        sheet.setStatus(TimesheetStatus.LOCKED);
        submission.setStatus(TimesheetStatus.APPROVED);
        submission.setTotalHours(new BigDecimal("8"));
        submission.setApprovedBillRate(new BigDecimal("75"));
        var assignment = assignments.findByProjectIdAndUserId(3L, 1L).orElseThrow();
        assignment.setStartDate(past.atDay(1));
        assignment.setEndDate(past.atEndOfMonth());

        service.openAfterApprovedRequest(4L, 3L, "Approved correction", 2L);

        assertEquals(TimesheetStatus.DRAFT, sheet.getStatus());
        assertEquals(TimesheetStatus.DRAFT, submission.getStatus());
        assertTrue(submission.isCorrectionOpen());
        assertNull(submission.getCorrectionUntil());
        assertNull(submission.getApprovedBillRate());
        add(past.atDay(10), "8", List.of());
    }

    @Test void futureMonthCannotBeSubmitted() {
        YearMonth future = YearMonth.now().plusMonths(1);
        sheet.setYear(future.getYear());
        sheet.setMonth(future.getMonthValue());
        assertThrows(IllegalArgumentException.class, () -> service.submitProjectTimesheet(4L, 3L, 1L));
        verify(submissions, never()).save(any());
    }

    @Test void approvedMonthFreezesAnotherDraftProject() {
        sheet.setStatus(TimesheetStatus.APPROVED);
        sheet.setApprovalFrozen(true);
        submission.setStatus(TimesheetStatus.DRAFT);
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "8", List.of()));
        verify(entries, never()).save(any());
    }

    @Test void ordinaryEditingRemainsOpenAfterMonthEnd() {
        sheet.setYear(2026);
        sheet.setMonth(9);
        assertTrue(TimesheetService.standardEditingOpen(sheet, LocalDate.of(2026, 10, 7)));
        assertTrue(TimesheetService.standardEditingOpen(sheet, LocalDate.of(2026, 10, 8)));
        assertTrue(TimesheetService.standardEditingOpen(sheet, LocalDate.of(2028, 1, 1)));
        assertFalse(TimesheetService.standardEditingOpen(sheet, LocalDate.of(2026, 8, 31)));
    }

    @Test void openingOneProjectKeepsOtherDraftProjectsFrozen() {
        admin.setRole(UserRole.PROJECT_ADMIN);
        sheet.setStatus(TimesheetStatus.APPROVED);
        sheet.setApprovalFrozen(true);
        submission.setStatus(TimesheetStatus.APPROVED);
        var otherProject = Project.builder().id(30L).companyId(1L).code("P2").name("Other project")
                .status(ProjectStatus.ACTIVE).build();
        var otherSubmission = TimesheetProjectSubmission.builder().id(31L).timesheet(sheet)
                .project(otherProject).status(TimesheetStatus.DRAFT).totalHours(BigDecimal.ZERO).build();
        when(projects.findById(30L)).thenReturn(Optional.of(otherProject));
        when(projectService.isAssigned(30L, 1L)).thenReturn(true);
        when(submissions.findByTimesheetIdAndProjectId(4L, 30L)).thenReturn(Optional.of(otherSubmission));
        service.openAfterApprovedRequest(4L, 3L, "Correct P1", 2L);
        assertTrue(sheet.isApprovalFrozen());
        assertThrows(IllegalArgumentException.class, () -> service.addTimeEntry(4L,
                workMonth.atDay(10), new BigDecimal("8"), "", 30L, List.of(), 1L));
        add(workMonth.atDay(10), "8", List.of());
    }

    @Test void approvedProjectStillRejectsChanges() {
        submission.setStatus(TimesheetStatus.APPROVED);
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "8", List.of()));
        verify(entries, never()).save(any());
    }

    @Test void lockedMonthStillRejectsDraftChanges() {
        sheet.setStatus(TimesheetStatus.LOCKED);
        assertThrows(IllegalArgumentException.class, () -> add(workMonth.atDay(10), "8", List.of()));
        verify(entries, never()).save(any());
    }
}
