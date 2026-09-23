package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.dto.SaveProjectRequest;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectPermissionsTest {
    @Mock ProjectRepository projects;
    @Mock ProjectAssignmentRepository assignments;
    @Mock ProjectHourPlanRepository plans;
    @Mock TimesheetRepository timesheets;
    @Mock TimesheetProjectSubmissionRepository submissions;
    @Mock UserRepository users;
    @Mock AuditService audit;
    @Mock NotificationService notifications;
    @InjectMocks ProjectService service;
    User superAdmin = User.builder().id(1L).role(UserRole.SUPER_ADMIN).build();
    User admin = User.builder().id(2L).role(UserRole.ADMIN).isActive(true).build();
    User manager = User.builder().id(3L).role(UserRole.EMPLOYEE).isActive(true).build();
    User approver = User.builder().id(4L).role(UserRole.EMPLOYEE).isActive(true).build();

    @Test void managerVisibilityExcludesUnrelatedProjectsAndEmployees() {
        var managed = Project.builder().id(10L).code("MANAGED").projectManager(manager).build();
        var member = Project.builder().id(11L).code("MEMBER").build();
        var unrelated = Project.builder().id(12L).code("OTHER").build();
        var mine = ProjectAssignment.builder().project(member).user(manager).isActive(true).build();
        var teammate = ProjectAssignment.builder().project(managed).user(approver).isActive(true).build();
        var other = ProjectAssignment.builder().project(unrelated).user(admin).isActive(true).build();
        when(projects.findAll()).thenReturn(List.of(managed, member, unrelated));
        when(assignments.findByUserId(3L)).thenReturn(List.of(mine));
        when(assignments.findAll()).thenReturn(List.of(mine, teammate, other));
        assertEquals(Set.of(10L, 11L), service.visibleProjectIds(manager));
        assertEquals(Set.of(3L, 4L), service.visibleEmployeeIds(manager));
        assertEquals(Set.of(10L, 11L, 12L), service.visibleProjectIds(admin));
    }

    @Test void dashboardBudgetUsesLifetimeHoursInsteadOfMonthlyHours() {
        var project = Project.builder().id(10L).code("ATLAS").name("Atlas").totalAllocatedHours(new BigDecimal("100")).build();
        when(projects.findAll()).thenReturn(List.of(project));
        when(projects.totalRecordedHours(10L)).thenReturn(new BigDecimal("85"));
        var result = service.getProjectHoursDashboard(2026,9,admin).get(0);
        assertEquals(new BigDecimal("100"),result.getBudgetHours());
        assertEquals(new BigDecimal("85"),result.getLifetimeLoggedHours());
        assertEquals(BigDecimal.ZERO,result.getTotalLoggedHours());
    }

    @Test void superAdminCanReadProjectsAndDashboard() {
        when(projects.findAll()).thenReturn(List.of());
        assertTrue(service.getProjects(superAdmin).isEmpty());
        assertTrue(service.getProjectHoursDashboard(2026, 9, superAdmin).isEmpty());
        verify(projects, times(2)).findAll();
    }

    @Test void superAdminCannotWriteProjectsAssignmentsOrEitherPlanVariant() {
        var date = LocalDate.of(2026, 9, 1);
        assertThrows(AccessDeniedException.class, () -> service.saveProject(null, new SaveProjectRequest(), superAdmin));
        assertThrows(AccessDeniedException.class, () -> service.saveProject(10L, new SaveProjectRequest(), superAdmin));
        assertThrows(AccessDeniedException.class, () -> service.assignEmployee(10L, 3L, date, date, BigDecimal.TEN, superAdmin));
        assertThrows(AccessDeniedException.class, () -> service.removeEmployee(10L, 3L, superAdmin));
        assertThrows(AccessDeniedException.class, () -> service.updateAssignmentDates(10L, 3L, date, date, BigDecimal.TEN, superAdmin));
        assertThrows(AccessDeniedException.class, () -> service.updatePlannedHours(10L, 3L, BigDecimal.TEN, superAdmin));
        assertThrows(AccessDeniedException.class, () -> service.updatePlannedHours(10L, 3L, 2026, 9, BigDecimal.TEN, superAdmin));
        verifyNoInteractions(projects, assignments, plans, audit);
    }

    @Test void adminCanCreateProjectWithoutPromotingEmployees() {
        var request = new SaveProjectRequest();
        request.setCode("P1");
        request.setName("Project");
        request.setProjectManagerId(3L);
        request.setProjectManagerHoursApproverId(4L);
        when(users.findById(3L)).thenReturn(Optional.of(manager));
        when(users.findById(4L)).thenReturn(Optional.of(approver));
        when(projects.save(any())).thenAnswer(call -> { Project p = call.getArgument(0); p.setId(10L); return p; });
        var result = service.saveProject(null, request, admin);
        assertEquals(3L, result.getProjectManagerId());
        assertEquals(4L, result.getProjectManagerHoursApproverId());
        assertEquals(UserRole.EMPLOYEE, manager.getRole());
        assertEquals(UserRole.EMPLOYEE, approver.getRole());
        verify(users, never()).save(any());
        verify(assignments, never()).save(any());
    }

    @Test void adminCanUseProjectManagerAsPmHoursApprover() {
        var request = new SaveProjectRequest();
        request.setCode("P1");
        request.setName("Project");
        request.setProjectManagerId(3L);
        request.setProjectManagerHoursApproverId(3L);
        when(users.findById(3L)).thenReturn(Optional.of(manager));
        when(projects.save(any())).thenAnswer(call -> { Project p = call.getArgument(0); p.setId(10L); return p; });

        var result = service.saveProject(null, request, admin);

        assertEquals(3L, result.getProjectManagerId());
        assertEquals(3L, result.getProjectManagerHoursApproverId());
    }

    @Test void employeeCannotPlanProjectHoursEvenForTheirManagedProject() {
        var project = Project.builder().id(10L).code("P1").projectManager(manager).build();
        var assignment = ProjectAssignment.builder().project(project).user(approver).isActive(true).build();
        assertThrows(AccessDeniedException.class, () -> service.updatePlannedHours(10L, 4L, BigDecimal.TEN, manager));
        assertThrows(AccessDeniedException.class, () -> service.updatePlannedHours(10L, 4L, BigDecimal.ONE, approver));
        verify(assignments, never()).save(any());
        assertNull(assignment.getPlannedHours());
    }

    @Test void managerSeesOnlyManagedApprovedOrAssignedProjects() {
        when(projects.existsByProjectManagerId(3L)).thenReturn(true);
        var managed = Project.builder().id(10L).code("A").projectManager(manager).build();
        var assigned = Project.builder().id(11L).code("B").build();
        var other = Project.builder().id(12L).code("C").build();
        when(projects.findAll()).thenReturn(List.of(managed, assigned, other));
        when(assignments.findByUserId(3L)).thenReturn(List.of(ProjectAssignment.builder().project(assigned).isActive(true).build()));
        assertEquals(List.of(10L, 11L), service.getProjects(manager).stream().map(p -> p.getId()).toList());
        assertEquals(List.of(10L, 11L), service.getProjectHoursDashboard(2026, 9, manager).stream().map(p -> p.getProjectId()).toList());
    }

    @Test void handoverTransfersPendingApprovalsButPreservesCompletedHistory() {
        var project = Project.builder().id(10L).code("P1").name("Project").projectManager(manager).projectManagerHoursApprover(approver).build();
        var sheet = Timesheet.builder().user(manager).build();
        var pending = TimesheetProjectSubmission.builder().id(20L).project(project).timesheet(sheet)
                .status(TimesheetStatus.SUBMITTED).assignedApprover(approver).build();
        var completed = TimesheetProjectSubmission.builder().id(21L).project(project).timesheet(sheet)
                .status(TimesheetStatus.APPROVED).assignedApprover(approver).approvedBy(approver).build();
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.save(any())).thenAnswer(call -> call.getArgument(0));
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findById(4L)).thenReturn(Optional.of(approver));
        when(submissions.findByProjectId(10L)).thenReturn(List.of(pending, completed));
        var request = SaveProjectRequest.builder().code("P1").name("Project").projectManagerId(2L).projectManagerHoursApproverId(4L).build();
        service.saveProject(10L, request, admin);
        assertEquals(admin, pending.getAssignedApprover());
        assertEquals(approver, completed.getAssignedApprover());
        assertEquals(approver, completed.getApprovedBy());
        assertEquals(TimesheetStatus.APPROVED, completed.getStatus());
        verify(notifications).markApprovalReassigned(20L);
        verify(notifications).createNotification(eq(2L), eq("TIMESHEET_SUBMITTED"), anyString(), anyString(), eq(20L), eq("TimesheetProjectSubmission"));
        verify(submissions, never()).save(completed);
    }

    @Test void inactiveAndSuperAdminCannotBeSelectedAsReviewers() {
        var request = SaveProjectRequest.builder().code("P1").name("Project").projectManagerId(3L).projectManagerHoursApproverId(4L).build();
        when(users.findById(3L)).thenReturn(Optional.of(manager));
        when(users.findById(4L)).thenReturn(Optional.of(approver));
        approver.setIsActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.saveProject(null, request, admin));
        approver.setIsActive(true);
        approver.setRole(UserRole.SUPER_ADMIN);
        assertThrows(IllegalArgumentException.class, () -> service.saveProject(null, request, admin));
        verify(projects, never()).save(any());
    }

    @Test void designatedApproverWithoutPmAssignmentCannotReadManagementPages() {
        when(projects.existsByProjectManagerIdOrProjectManagerHoursApproverId(4L, 4L)).thenReturn(true);
        assertTrue(service.canReviewProjects(4L));
        assertThrows(AccessDeniedException.class, () -> service.getProjects(approver));
        assertThrows(IllegalArgumentException.class, () -> service.getProjectHoursDashboard(2026, 9, approver));
        verify(projects, never()).findAll();
    }

    @Test void regularEmployeeCannotReadManagementProjects() {
        assertThrows(AccessDeniedException.class, () -> service.getProjects(manager));
        assertThrows(IllegalArgumentException.class, () -> service.getProjectHoursDashboard(2026, 9, manager));
        verify(projects, never()).findAll();
    }

    @Test void pendingApprovalBlocksOffboarding() {
        var project = Project.builder().id(10L).build();
        var assignment = ProjectAssignment.builder().project(project).user(manager).isActive(true).build();
        when(assignments.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.of(assignment));
        when(submissions.findByProjectIdAndTimesheetUserId(10L, 3L)).thenReturn(List.of(
                TimesheetProjectSubmission.builder().status(TimesheetStatus.SUBMITTED).build()));
        assertThrows(IllegalArgumentException.class, () -> service.removeEmployee(10L, 3L, admin));
        assertTrue(assignment.getIsActive());
        verify(assignments, never()).save(any());
    }

    @Test void offboardingFreezesApprovedHoursAndTransfersManager() {
        var project = Project.builder().id(10L).projectManager(manager).build();
        var assignment = ProjectAssignment.builder().project(project).user(manager).isActive(true).plannedHours(new BigDecimal("100")).build();
        var replacementUser = User.builder().id(5L).isActive(true).build();
        when(users.findById(5L)).thenReturn(Optional.of(replacementUser));
        var replacement = ProjectAssignment.builder().project(project).user(replacementUser).isActive(true).build();
        when(assignments.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.of(assignment));
        when(assignments.findByProjectIdAndUserId(10L, 5L)).thenReturn(Optional.of(replacement));
        when(submissions.findByProjectIdAndTimesheetUserId(10L, 3L)).thenReturn(List.of(
                TimesheetProjectSubmission.builder().status(TimesheetStatus.APPROVED).totalHours(new BigDecimal("20")).build(),
                TimesheetProjectSubmission.builder().status(TimesheetStatus.LOCKED).totalHours(new BigDecimal("15")).build(),
                TimesheetProjectSubmission.builder().status(TimesheetStatus.REJECTED).totalHours(new BigDecimal("10")).build()));
        assertThrows(IllegalArgumentException.class, () -> service.removeEmployee(10L, 3L, admin));
        service.removeEmployee(10L, 3L, 5L, admin);
        assertFalse(assignment.getIsActive());
        assertEquals(new BigDecimal("35"), assignment.getPlannedHours());
        assertEquals(5L, project.getProjectManager().getId());
    }

    @Test void closureBlocksUnsubmittedAndUnapprovedHours() {
        for (ProjectStatus target : List.of(ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED)) {
            for (TimesheetStatus pending : List.of(TimesheetStatus.DRAFT, TimesheetStatus.REJECTED, TimesheetStatus.SUBMITTED, TimesheetStatus.CHANGE_REQUESTED)) {
                Project project = Project.builder().id(10L).status(ProjectStatus.ACTIVE).build();
                when(projects.findById(10L)).thenReturn(Optional.of(project));
                when(submissions.findByProjectId(10L)).thenReturn(List.of(TimesheetProjectSubmission.builder().status(pending).totalHours(BigDecimal.TEN).build()));
                SaveProjectRequest request = new SaveProjectRequest();
                request.setCode("P1"); request.setName("Project"); request.setProjectManagerId(3L); request.setProjectManagerHoursApproverId(4L); request.setStatus(target);
                assertThrows(IllegalArgumentException.class, () -> service.saveProject(10L, request, admin));
                assertEquals(ProjectStatus.ACTIVE, project.getStatus());
            }
        }
        verify(projects, never()).save(any());
    }

    @Test void closureBlocksLoggedHoursWithoutSubmission() {
        when(projects.findById(10L)).thenReturn(Optional.of(Project.builder().id(10L).status(ProjectStatus.ACTIVE).build()));
        when(submissions.countUnfinalizedEntries(eq(10L), anyList())).thenReturn(1L);
        SaveProjectRequest request = new SaveProjectRequest();
        request.setCode("P1"); request.setName("Project"); request.setProjectManagerId(3L); request.setProjectManagerHoursApproverId(4L); request.setStatus(ProjectStatus.ARCHIVED);
        assertThrows(IllegalArgumentException.class, () -> service.saveProject(10L, request, admin));
        verify(projects, never()).save(any());
    }
    private void stubExistingManager() {
        when(assignments.findByProjectIdAndUserId(10L, 3L)).thenReturn(Optional.of(ProjectAssignment.builder().isActive(true).startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(1)).billRate(BigDecimal.TEN).plannedHours(BigDecimal.TEN).build()));
    }
}
