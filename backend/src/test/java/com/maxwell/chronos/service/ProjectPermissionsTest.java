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
    @InjectMocks ProjectService service;
    User superAdmin = User.builder().id(1L).role(UserRole.SUPER_ADMIN).build();
    User admin = User.builder().id(2L).role(UserRole.ADMIN).build();
    User manager = User.builder().id(3L).role(UserRole.EMPLOYEE).build();
    User approver = User.builder().id(4L).role(UserRole.EMPLOYEE).build();

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

    @Test void employeeCanPlanOnlyTheirManagedProject() {
        var project = Project.builder().id(10L).code("P1").projectManager(manager).build();
        var assignment = ProjectAssignment.builder().project(project).user(approver).isActive(true).build();
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(assignments.findByProjectIdAndUserId(10L, 4L)).thenReturn(Optional.of(assignment));
        service.updatePlannedHours(10L, 4L, BigDecimal.TEN, manager);
        assertEquals(BigDecimal.TEN, assignment.getPlannedHours());
        assertThrows(AccessDeniedException.class, () -> service.updatePlannedHours(10L, 4L, BigDecimal.ONE, approver));
        verify(assignments, times(1)).save(any());
    }

    @Test void employeeDashboardIsScopedToManagedProjects() {
        when(projects.existsByProjectManagerIdOrProjectManagerHoursApproverId(3L, 3L)).thenReturn(true);
        service.getProjectHoursDashboard(2026, 9, manager);
        verify(projects).findByProjectManagerIdAndStatus(3L, ProjectStatus.ACTIVE);
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
