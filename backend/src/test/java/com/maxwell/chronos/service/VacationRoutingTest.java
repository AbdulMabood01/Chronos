package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacationRoutingTest {
    private final VacationRequestRepository vacations = mock(VacationRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectAssignmentRepository assignments = mock(ProjectAssignmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final VacationService service = new VacationService(vacations, users, mock(TimesheetRepository.class),
            mock(TimesheetProjectSubmissionRepository.class), assignments, mock(AuditService.class), notifications);
    private final User employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
    private final User admin = User.builder().id(2L).role(UserRole.SUPER_ADMIN).build();
    private final User manager = User.builder().id(3L).role(UserRole.EMPLOYEE).isActive(true).build();

    private VacationRequest request(VacationStatus status) {
        var request = VacationRequest.builder().id(10L).user(employee).status(status)
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 10, 2)).build();
        when(vacations.findById(10L)).thenReturn(Optional.of(request));
        when(vacations.save(request)).thenReturn(request);
        return request;
    }

    @Test void managersCannotApproveOrReject() {
        var request = request(VacationStatus.SUBMITTED);
        for (UserRole role : List.of(UserRole.EMPLOYEE, UserRole.ADMIN)) {
            manager.setRole(role);
            when(users.findById(3L)).thenReturn(Optional.of(manager));
            assertThrows(IllegalArgumentException.class, () -> service.approveVacationRequest(10L, 3L));
            assertThrows(IllegalArgumentException.class, () -> service.rejectVacationRequest(10L, "reason", 3L));
        }
        assertEquals(VacationStatus.SUBMITTED, request.getStatus());
        verify(vacations, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test void superAdminCanApproveSubmittedVacation() {
        var request = request(VacationStatus.SUBMITTED);
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));

        var result = service.approveVacationRequest(10L, 2L);

        assertEquals(VacationStatus.APPROVED, request.getStatus());
        assertEquals(VacationStatus.APPROVED, result.getStatus());
        assertSame(admin, request.getApprovedBy());
        verify(vacations).save(request);
        verify(notifications).createNotification(eq(1L), eq("VACATION_APPROVED"), anyString(), anyString(), eq(10L), eq("VacationRequest"));
    }

    @Test void submissionAndApprovalNotifyOnlyActiveProjectManagersOnce() {
        request(VacationStatus.DRAFT);
        var projects = new ArrayList<ProjectAssignment>();
        for (ProjectStatus status : ProjectStatus.values()) {
            var recipient = status == ProjectStatus.ACTIVE ? manager
                    : User.builder().id(100L + status.ordinal()).isActive(true).build();
            projects.add(ProjectAssignment.builder().project(Project.builder().status(status)
                    .isActive(true).projectManager(recipient).build()).build());
        }
        projects.add(projects.get(ProjectStatus.ACTIVE.ordinal()));
        projects.add(ProjectAssignment.builder().project(Project.builder().status(ProjectStatus.ACTIVE)
                .isActive(false).projectManager(User.builder().id(200L).isActive(true).build()).build()).build());
        projects.add(ProjectAssignment.builder().project(Project.builder().status(ProjectStatus.ACTIVE)
                .isActive(true).projectManager(User.builder().id(201L).isActive(false).build()).build()).build());
        when(assignments.findByUserIdAndIsActiveTrue(1L)).thenReturn(projects);
        when(users.findByRole(UserRole.SUPER_ADMIN)).thenReturn(List.of(admin));
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));

        service.submitVacationRequest(10L, 1L);
        verify(notifications).createNotification(eq(2L), eq("VACATION_SUBMITTED"), anyString(), anyString(), eq(10L), eq("VacationRequest"));
        verify(notifications).createNotification(eq(3L), eq("VACATION_SUBMITTED"), anyString(), anyString(), eq(10L), eq("VacationRequest"));
        service.approveVacationRequest(10L, 2L);
        verify(notifications).createNotification(eq(1L), eq("VACATION_APPROVED"), anyString(), anyString(), eq(10L), eq("VacationRequest"));
        verify(notifications).createNotification(eq(3L), eq("VACATION_APPROVED"), anyString(), anyString(), eq(10L), eq("VacationRequest"));
        verifyNoMoreInteractions(notifications);
    }
}
