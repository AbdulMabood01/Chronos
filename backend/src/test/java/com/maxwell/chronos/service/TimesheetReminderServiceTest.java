package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.*;
import com.maxwell.chronos.enums.*;
import com.maxwell.chronos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TimesheetReminderServiceTest {
    final UserRepository users = mock(UserRepository.class);
    final TimesheetRepository sheets = mock(TimesheetRepository.class);
    final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    final TimesheetReminderEmailService email = mock(TimesheetReminderEmailService.class);
    final TransactionTemplate transactions = mock(TransactionTemplate.class);
    final TimesheetReminderService service = new TimesheetReminderService(users, sheets, settings,
            notifications, mock(AuditService.class), email, transactions);
    User employee;
    Timesheet sheet;

    @BeforeEach void setup() {
        employee = User.builder().id(1L).email("employee@example.com").role(UserRole.EMPLOYEE)
                .isActive(true).build();
        sheet = Timesheet.builder().id(10L).user(employee).status(TimesheetStatus.DRAFT).build();
        when(users.findByIsActiveTrue()).thenReturn(List.of(employee));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        when(sheets.findPeriodForUpdate(eq(1L), anyInt(), anyInt())).thenReturn(Optional.of(sheet));
        when(sheets.save(any())).thenAnswer(i -> i.getArgument(0));
        doAnswer(i -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = i.getArgument(0);
            action.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    void run(String local) {
        service.sendTimesheetReminders(LocalDateTime.parse(local).atZone(ZoneId.of(employee.getTimezone())).toInstant());
    }

    @Test void fridayAtEightAndOnlyOncePerDate() {
        run("2026-09-18T19:55:00");
        verifyNoInteractions(email);
        run("2026-09-18T20:00:00");
        run("2026-09-18T20:05:00");
        verify(email).sendReminder(employee, YearMonth.of(2026, 9));
        assertEquals(LocalDateTime.parse("2026-09-18T20:00:00"), sheet.getLastReminderSentAt());
    }

    @Test void noReminderOnOrdinaryWeekday() {
        run("2026-09-17T20:00:00");
        verifyNoInteractions(email);
    }

    @Test void weekendMonthEndAndLeapDayAreIncluded() {
        run("2026-05-31T20:00:00");
        verify(email).sendReminder(employee, YearMonth.of(2026, 5));
        run("2028-02-29T20:00:00");
        verify(email).sendReminder(employee, YearMonth.of(2028, 2));
    }

    @Test void fridayMonthEndSendsOneReminder() {
        run("2026-07-31T20:00:00");
        run("2026-07-31T21:00:00");
        verify(email).sendReminder(employee, YearMonth.of(2026, 7));
    }

    @Test void usesEmployeeDateAcrossUtcMonthBoundaryAndDst() {
        service.sendTimesheetReminders(Instant.parse("2026-08-01T01:00:00Z"));
        verify(email).sendReminder(employee, YearMonth.of(2026, 7));
        employee.setTimezone("Asia/Kolkata");
        service.sendTimesheetReminders(Instant.parse("2026-09-18T14:30:00Z"));
        verify(email).sendReminder(employee, YearMonth.of(2026, 9));
        employee.setTimezone("America/Chicago");
        service.sendTimesheetReminders(Instant.parse("2026-12-05T02:00:00Z"));
        verify(email).sendReminder(employee, YearMonth.of(2026, 12));
    }

    @Test void skipsSubmittedApprovedLockedAndChangeRequested() {
        for (TimesheetStatus status : List.of(TimesheetStatus.SUBMITTED, TimesheetStatus.APPROVED,
                TimesheetStatus.LOCKED, TimesheetStatus.CHANGE_REQUESTED)) {
            sheet.setStatus(status);
            run("2026-09-18T20:00:00");
        }
        verifyNoInteractions(email);
    }

    @Test void remindsRejectedAndMissingTimesheets() {
        sheet.setStatus(TimesheetStatus.REJECTED);
        run("2026-09-18T20:00:00");
        when(sheets.findPeriodForUpdate(1L, 2026, 9)).thenReturn(Optional.empty());
        run("2026-09-25T20:00:00");
        verify(email, times(2)).sendReminder(employee, YearMonth.of(2026, 9));
        verify(sheets, atLeastOnce()).save(argThat(t -> t.getUser() == employee && t.getStatus() == TimesheetStatus.DRAFT));
    }

    @Test void disabledOrInactiveOrSuperAdminAreSkipped() {
        employee.setIsActive(false);
        run("2026-09-18T20:00:00");
        employee.setIsActive(true);
        employee.setRole(UserRole.SUPER_ADMIN);
        run("2026-09-18T20:00:00");
        employee.setRole(UserRole.EMPLOYEE);
        when(settings.findBySettingKey("timesheet.reminders.enabled")).thenReturn(Optional.of(
                SystemSetting.builder().settingValue("false").build()));
        run("2026-09-18T20:00:00");
        verifyNoInteractions(email);
    }

    @Test void failedEmailIsNotMarkedSentAndOtherUsersStillReceiveReminders() {
        User second = User.builder().id(2L).isActive(true).role(UserRole.ADMIN).build();
        when(users.findByIsActiveTrue()).thenReturn(List.of(employee, second));
        when(users.findForUpdate(2L)).thenReturn(Optional.of(second));
        when(sheets.findPeriodForUpdate(2L, 2026, 9)).thenReturn(Optional.of(
                Timesheet.builder().id(11L).status(TimesheetStatus.DRAFT).build()));
        doThrow(new IllegalStateException("SMTP unavailable")).when(email).sendReminder(eq(employee), any());
        run("2026-09-18T20:00:00");
        assertNull(sheet.getLastReminderSentAt());
        verify(email).sendReminder(second, YearMonth.of(2026, 9));
        verify(notifications, never()).createNotification(eq(1L), any(), any(), any(), any(), any());
        doNothing().when(email).sendReminder(eq(employee), any());
        run("2026-09-18T20:05:00");
        assertNotNull(sheet.getLastReminderSentAt());
    }
}
