package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.dto.ProjectHealthDTO;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectHealthServiceTest {
    final LocalDate today = LocalDate.of(2026, 9, 15);
    final User employee = User.builder().id(3L).isActive(true).role(UserRole.EMPLOYEE).build();
    final Project project = Project.builder().id(1L).code("ONE").name("One").status(ProjectStatus.ACTIVE).totalAllocatedHours(new BigDecimal("100")).build();
    ProjectAssignment assignment(Project p, String start, String end, int hours) {
        return ProjectAssignment.builder().project(p).user(employee).isActive(true).startDate(LocalDate.parse(start))
                .endDate(LocalDate.parse(end)).plannedHours(BigDecimal.valueOf(hours)).build();
    }
    Timesheet sheet(int month) { return Timesheet.builder().id((long) month).user(employee).year(2026).month(month).build(); }
    TimeEntry entry(int month, int hours) { return TimeEntry.builder().project(project).timesheet(sheet(month)).entryDate(LocalDate.of(2026, month, 1)).hours(BigDecimal.valueOf(hours)).build(); }
    TimesheetProjectSubmission submission(int month, TimesheetStatus status) {
        return TimesheetProjectSubmission.builder().project(project).timesheet(sheet(month)).status(status).totalHours(BigDecimal.TEN).build();
    }
    ProjectHealthDTO evaluate(List<ProjectAssignment> team, List<TimeEntry> entries, List<TimesheetProjectSubmission> submissions) {
        return ProjectHealthService.evaluate(project, team, team, entries, submissions, today);
    }
    boolean has(ProjectHealthDTO health, String code) { return health.signals().stream().anyMatch(s -> s.code().equals(code)); }

    @Test void lifetimeBurnIncludesPreviousMonthsAndExplainsTimeline() {
        var health = evaluate(List.of(assignment(project, "2026-08-01", "2026-11-01", 100)), List.of(entry(8, 40), entry(9, 42)), List.of(submission(8, TimesheetStatus.APPROVED)));
        assertEquals("AT_RISK", health.status());
        assertEquals(new BigDecimal("82"), health.loggedHours());
        assertEquals(new BigDecimal("18"), health.remainingHours());
        assertEquals(82.0, health.hoursUtilization());
        assertTrue(has(health, "BURN_AHEAD"));
        assertEquals(0, health.missingTimesheets());
    }
    @Test void distinguishesHealthyAttentionAndRiskAtBudgetThresholds() {
        var team = List.of(assignment(project, "2026-09-01", "2026-09-30", 100));
        assertEquals("HEALTHY", evaluate(team, List.of(entry(9, 20)), List.of()).status());
        // Evaluate later in the window so the burn-ahead rule does not hide the budget threshold.
        for (int hours : new int[]{79, 80, 94, 95, 101}) {
            var p = Project.builder().id(1L).code("ONE").status(ProjectStatus.ACTIVE).totalAllocatedHours(BigDecimal.valueOf(100)).build();
            var health = ProjectHealthService.evaluate(p, team, team, List.of(entry(9, hours)), List.of(), LocalDate.of(2026, 9, 15));
            assertEquals(hours >= 80, has(health, "HOURS_USED") || has(health, "HOURS_EXCEEDED"));
            if (hours >= 95) assertEquals("AT_RISK", health.status());
        }
    }
    @Test void zeroBudgetDoesNotDivideByZeroAndUnknownBudgetIsNotHealthy() {
        project.setTotalAllocatedHours(BigDecimal.ZERO);
        var health = evaluate(List.of(), List.of(entry(9, 1)), List.of());
        assertNull(health.hoursUtilization());
        assertTrue(has(health, "HOURS_EXCEEDED"));
        project.setTotalAllocatedHours(null);
        assertTrue(has(evaluate(List.of(), List.of(), List.of()), "HOURS_UNKNOWN"));
    }
    @Test void derivesBudgetFromAssignmentsAndKeepsNegativeRemaining() {
        project.setTotalAllocatedHours(null);
        var health = evaluate(List.of(assignment(project, "2026-09-01", "2026-10-30", 20)), List.of(entry(9, 25)), List.of());
        assertEquals(BigDecimal.valueOf(20), health.allocatedHours());
        assertEquals(BigDecimal.valueOf(-5), health.remainingHours());
    }
    @Test void missingSubmissionsAreDeduplicatedAndCurrentMonthIsNotDue() {
        var team = List.of(assignment(project, "2026-07-01", "2026-10-30", 100));
        var health = evaluate(team, List.of(entry(7, 10), entry(7, 15), entry(9, 10)), List.of(submission(7, TimesheetStatus.REJECTED)));
        assertEquals(2, health.missingTimesheets()); // July rejected and August absent.
        assertEquals(0, health.pendingApprovals());
        assertTrue(has(health, "REJECTED"));
        health = evaluate(team, List.of(entry(7, 10)), List.of(submission(7, TimesheetStatus.SUBMITTED), submission(8, TimesheetStatus.CHANGE_REQUESTED)));
        assertEquals(0, health.missingTimesheets());
        assertEquals(2, health.pendingApprovals());
    }
    @Test void approvedAndLockedSubmissionsClearMissingMonths() {
        var health = evaluate(List.of(assignment(project, "2026-07-01", "2026-10-30", 100)), List.of(),
                List.of(submission(7, TimesheetStatus.APPROVED), submission(8, TimesheetStatus.LOCKED)));
        assertEquals(0, health.missingTimesheets());
        assertEquals(0, health.pendingApprovals());
    }
    @Test void futureAssignmentsAndWeekendOnlyMonthsAreNotMissing() {
        assertEquals(0, evaluate(List.of(assignment(project, "2026-10-01", "2026-11-01", 20)), List.of(), List.of()).missingTimesheets());
        assertEquals(0, evaluate(List.of(assignment(project, "2026-08-01", "2026-08-02", 20)), List.of(), List.of()).missingTimesheets());
        assertEquals(22, ProjectHealthService.weekdays(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
    }
    @Test void capacityCountsPeopleAcrossOverlappingProjectsOnly() {
        var other = Project.builder().id(2L).status(ProjectStatus.ACTIVE).build();
        var mine = assignment(project, "2026-09-01", "2026-09-30", 100);
        var elsewhere = assignment(other, "2026-09-01", "2026-09-30", 100);
        var health = ProjectHealthService.evaluate(project, List.of(mine), List.of(mine, elsewhere), List.of(), List.of(), today);
        assertEquals(1, health.overallocatedResources());
        elsewhere.setStartDate(LocalDate.of(2026, 10, 1));
        elsewhere.setEndDate(LocalDate.of(2026, 10, 31));
        assertEquals(0, ProjectHealthService.evaluate(project, List.of(mine), List.of(mine, elsewhere), List.of(), List.of(), today).overallocatedResources());
        elsewhere.setStartDate(mine.getStartDate());
        elsewhere.setEndDate(mine.getEndDate());
        other.setStatus(ProjectStatus.ON_HOLD);
        assertEquals(0, ProjectHealthService.evaluate(project, List.of(mine), List.of(mine, elsewhere), List.of(), List.of(), today).overallocatedResources());
    }
    @Test void deadlineUsesAssignmentWindowAndClosedProjectsPauseDeliveryAlerts() {
        var team = List.of(assignment(project, "2026-09-01", "2026-09-14", 100));
        assertTrue(has(evaluate(team, List.of(), List.of()), "WINDOW_ENDED"));
        project.setStatus(ProjectStatus.COMPLETED);
        var health = evaluate(team, List.of(), List.of(submission(8, TimesheetStatus.SUBMITTED)));
        assertFalse(health.monitored());
        assertFalse(has(health, "WINDOW_ENDED"));
        assertEquals("ATTENTION_NEEDED", health.status());
        assertEquals(1, health.pendingApprovals());
    }
    @Test void permissionsRejectOrdinaryEmployeesAndLimitReturnedProjects() {
        var visibility = mock(ProjectService.class);
        var projects = mock(ProjectRepository.class);
        var assignments = mock(ProjectAssignmentRepository.class);
        var entries = mock(TimeEntryRepository.class);
        var submissions = mock(TimesheetProjectSubmissionRepository.class);
        var service = new ProjectHealthService(visibility, projects, assignments, entries, submissions);
        assertThrows(AccessDeniedException.class, () -> service.overview(null));
        assertThrows(AccessDeniedException.class, () -> service.overview(employee));
        verifyNoInteractions(projects, assignments, entries, submissions);
        when(visibility.canReviewProjects(employee.getId())).thenReturn(true);
        when(visibility.visibleProjectIds(employee)).thenReturn(Set.of(1L));
        when(projects.findAllById(Set.of(1L))).thenReturn(List.of(project));
        assertEquals(List.of(1L), service.overview(employee).stream().map(ProjectHealthDTO::projectId).toList());
        verify(entries).findForHealth(eq(Set.of(1L)), any());
        verify(submissions).findForHealth(Set.of(1L));
        verify(projects, never()).findAll();
    }
}
