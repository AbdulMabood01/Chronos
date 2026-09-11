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
        verify(assignments).save(any());
    }

    @Test void employeeCanPlanOnlyTheirManagedProject() {
        var project = Project.builder().id(10L).code("P1").projectManager(manager).build();
        var assignment = ProjectAssignment.builder().project(project).user(approver).build();
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
}
