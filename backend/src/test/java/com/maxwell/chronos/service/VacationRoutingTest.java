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
            mock(TimesheetProjectSubmissionRepository.class), assignments, mock(AuditService.class), notifications,
            mock(LeaveBalanceService.class));
    private final User employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
    private final User admin = User.builder().id(2L).role(UserRole.ADMIN).build();
    private final User manager = User.builder().id(3L).role(UserRole.EMPLOYEE).isActive(true).build();

    private VacationRequest request(VacationStatus status) {
        var request = VacationRequest.builder().id(10L).user(employee).status(status)
                .vacationType(VacationType.VACATION)
                .startDate(LocalDate.of(2026, 10, 1)).endDate(LocalDate.of(2026, 10, 2)).build();
        when(vacations.findById(10L)).thenReturn(Optional.of(request));
        when(vacations.save(request)).thenReturn(request);
        return request;
    }

    @Test void managersCannotApproveOrReject() {
        var request = request(VacationStatus.SUBMITTED);
        for (UserRole role : List.of(UserRole.EMPLOYEE, UserRole.PROJECT_ADMIN)) {
            manager.setRole(role);
            when(users.findById(3L)).thenReturn(Optional.of(manager));
            assertThrows(IllegalArgumentException.class, () -> service.approveVacationRequest(10L, 3L));
            assertThrows(IllegalArgumentException.class, () -> service.rejectVacationRequest(10L, "reason", 3L));
        }
        assertEquals(VacationStatus.SUBMITTED, request.getStatus());
        verify(vacations, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test void systemAdminCanApproveSubmittedVacation() {
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

    @Test void overlappingSubmittedLeaveCannotBeSubmittedOrApproved() {
        var draft = request(VacationStatus.DRAFT);
        var existing = VacationRequest.builder().id(11L).user(employee).status(VacationStatus.APPROVED)
                .startDate(LocalDate.of(2026, 10, 2)).endDate(LocalDate.of(2026, 10, 3)).build();
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(vacations.findByUserIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                eq(1L), anyList(), any(), any())).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class, () -> service.submitVacationRequest(10L, 1L));
        assertEquals(VacationStatus.DRAFT, draft.getStatus());
        draft.setStatus(VacationStatus.SUBMITTED);
        assertThrows(IllegalArgumentException.class, () -> service.approveVacationRequest(10L, 2L));
        assertEquals(VacationStatus.SUBMITTED, draft.getStatus());
        verify(vacations, never()).save(any());
    }

    @Test void vacationRejectionRequiresAReason() {
        var submitted = request(VacationStatus.SUBMITTED);
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.rejectVacationRequest(10L, " ", 2L));
        assertEquals(VacationStatus.SUBMITTED, submitted.getStatus());
    }

    @Test void specialLeaveRequiresAnExplicitAccountingDecision() {
        var request = request(VacationStatus.SUBMITTED);
        request.setVacationType(VacationType.SPECIAL);
        request.setSpecialReason("Military");
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        assertThrows(IllegalArgumentException.class, () -> service.approveVacationRequest(10L, 2L));
        service.approveVacationRequest(10L, 2L, LeaveAccountingType.PAID_NO_QUOTA);
        assertEquals(LeaveAccountingType.PAID_NO_QUOTA, request.getAccountingType());
        assertEquals(VacationStatus.APPROVED, request.getStatus());
    }

    @Test void specialLeaveRequiresAReasonAndOtherNeedsNotes() {
        when(users.findById(1L)).thenReturn(Optional.of(employee));
        var start = LocalDate.of(2026, 10, 1);
        assertThrows(IllegalArgumentException.class, () -> service.createVacationRequest(1L, start, start,
                VacationType.SPECIAL, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.createVacationRequest(1L, start, start,
                VacationType.SPECIAL, null, "Other"));
        verify(vacations, never()).save(any());
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
        when(users.findByRole(UserRole.ADMIN)).thenReturn(List.of(admin));
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
