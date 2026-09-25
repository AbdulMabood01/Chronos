package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VacationConflictTest {
    @Test void systemAdminCannotSubmitExistingDraft() {
        var vacations = mock(VacationRequestRepository.class);
        var service = new VacationService(vacations, mock(UserRepository.class), mock(TimesheetRepository.class),
                mock(TimesheetProjectSubmissionRepository.class), mock(ProjectAssignmentRepository.class),
                mock(AuditService.class), mock(NotificationService.class));
        var user = User.builder().id(1L).role(UserRole.ADMIN).build();
        var vacation = VacationRequest.builder().id(3L).user(user).status(VacationStatus.DRAFT).build();
        when(vacations.findById(3L)).thenReturn(Optional.of(vacation));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.submitVacationRequest(3L, 1L));
        verify(vacations, never()).save(any());
        assertEquals(VacationStatus.DRAFT, vacation.getStatus());
    }

    @Test void onlyAdminReceivesPendingVacationRequests() {
        var vacations = mock(VacationRequestRepository.class);
        var assignments = mock(ProjectAssignmentRepository.class);
        var service = new VacationService(vacations, mock(UserRepository.class), mock(TimesheetRepository.class),
                mock(TimesheetProjectSubmissionRepository.class), assignments, mock(AuditService.class), mock(NotificationService.class));
        var employee = User.builder().id(1L).role(UserRole.EMPLOYEE).build();
        var manager = User.builder().id(2L).role(UserRole.EMPLOYEE).isActive(true).build();
        var project = Project.builder().id(4L).projectManager(manager).build();
        var vacation = VacationRequest.builder().id(3L).user(employee).status(VacationStatus.SUBMITTED).build();
        when(vacations.findByStatus(VacationStatus.SUBMITTED)).thenReturn(List.of(vacation));
        when(assignments.findByUserIdAndIsActiveTrue(1L)).thenReturn(List.of(ProjectAssignment.builder().project(project).build()));
        assertTrue(service.getPendingVacationRequests(manager).isEmpty());
        assertEquals(1, service.getPendingVacationRequests(User.builder().id(10L).role(UserRole.ADMIN).build()).size());
        assertTrue(service.getPendingVacationRequests(User.builder().id(9L).role(UserRole.EMPLOYEE).build()).isEmpty());
        assertTrue(service.getPendingVacationRequests(employee).isEmpty());
    }

    @Test void finalizedHoursAreProtectedAndDraftTotalsStayConsistent() {
        var vacations = mock(VacationRequestRepository.class);
        var users = mock(UserRepository.class);
        var sheets = mock(TimesheetRepository.class);
        var submissions = mock(TimesheetProjectSubmissionRepository.class);
        var service = new VacationService(vacations, users, sheets, submissions, mock(ProjectAssignmentRepository.class),
                mock(AuditService.class), mock(NotificationService.class));
        var employee = User.builder().id(1L).build();
        var admin = User.builder().id(2L).role(UserRole.ADMIN).build();
        var date = LocalDate.of(2026,9,10);
        var vacation = VacationRequest.builder().id(3L).user(employee).startDate(date).endDate(date)
                .status(VacationStatus.SUBMITTED).build();
        var project = Project.builder().id(4L).build();
        var sheet = Timesheet.builder().id(5L).user(employee).status(TimesheetStatus.APPROVED).build();
        var entry = TimeEntry.builder().id(6L).project(project).entryDate(date).hours(BigDecimal.TEN).build();
        entry.getSessions().add(TimeEntrySession.builder().build());
        sheet.getTimeEntries().add(entry);
        var submission = TimesheetProjectSubmission.builder().project(project).status(TimesheetStatus.APPROVED)
                .totalHours(BigDecimal.TEN).build();
        when(users.findById(2L)).thenReturn(Optional.of(admin));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        when(vacations.findById(3L)).thenReturn(Optional.of(vacation));
        when(vacations.save(vacation)).thenReturn(vacation);
        when(sheets.findPeriodForUpdate(1L,2026,9)).thenReturn(Optional.of(sheet));
        when(submissions.findByTimesheetIdAndProjectId(5L,4L)).thenReturn(Optional.of(submission));
        when(submissions.findByTimesheetId(5L)).thenReturn(List.of(submission));
        assertThrows(IllegalArgumentException.class, () -> service.approveVacationRequest(3L,2L));
        assertEquals(BigDecimal.TEN,entry.getHours());
        // Model a separate request after the failed transaction has rolled back and the sheet was reopened.
        vacation.setStatus(VacationStatus.SUBMITTED);
        sheet.setStatus(TimesheetStatus.DRAFT);
        submission.setStatus(TimesheetStatus.DRAFT);
        service.approveVacationRequest(3L,2L);
        assertEquals(BigDecimal.ZERO,entry.getHours());
        assertEquals(BigDecimal.ZERO,sheet.getTotalHours());
        assertEquals(BigDecimal.ZERO,submission.getTotalHours());
        assertTrue(entry.getSessions().isEmpty());
    }
}
