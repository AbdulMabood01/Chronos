package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.SystemSetting;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.SystemSettingRepository;
import com.maxwell.chronos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TimesheetReminderServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final TimesheetPeriodService periods = mock(TimesheetPeriodService.class);
    private final TimesheetReminderEmailService email = mock(TimesheetReminderEmailService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final TimesheetReminderService service = new TimesheetReminderService(users, settings, db, periods,
            email, notifications, transactions);
    private final User employee = User.builder().id(1L).email("employee@example.com").timezone("UTC").build();

    @BeforeEach void setup() {
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of("user_id", 1L, "project_id", 4L, "code", "ATLAS")));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(employee));
        when(periods.period(eq(4L), any(LocalDate.class))).thenAnswer(call -> {
            LocalDate day = call.getArgument(1);
            return new TimesheetPeriodService.Period(day, day, "DAILY");
        });
        doAnswer(call -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action = call.getArgument(0);
            action.accept(mock(org.springframework.transaction.TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    @Test void sendsFivePmReminderOnceForTheClosingPeriod() {
        LocalDate day = LocalDate.of(2026, 9, 18);
        when(periods.markReminder(1L, 4L, new TimesheetPeriodService.Period(day, day, "DAILY"), false))
                .thenReturn(true, false);
        service.sendTimesheetReminders(Instant.parse("2026-09-18T16:59:00Z"));
        verifyNoInteractions(email);
        service.sendTimesheetReminders(Instant.parse("2026-09-18T17:00:00Z"));
        service.sendTimesheetReminders(Instant.parse("2026-09-18T17:05:00Z"));
        verify(email, times(1)).sendPeriodEmail(employee, "ATLAS", day, day, false);
    }

    @Test void sendsLateEmailJustAfterMidnightForTheMissedPeriod() {
        LocalDate yesterday = LocalDate.of(2026, 9, 18);
        when(periods.markReminder(1L, 4L, new TimesheetPeriodService.Period(yesterday, yesterday, "DAILY"), true))
                .thenReturn(true);
        service.sendTimesheetReminders(Instant.parse("2026-09-19T00:00:00Z"));
        verifyNoInteractions(email);
        service.sendTimesheetReminders(Instant.parse("2026-09-19T00:01:00Z"));
        verify(email).sendPeriodEmail(employee, "ATLAS", yesterday, yesterday, true);
    }

    @Test void disabledRemindersDoNotQueryAssignmentsOrSendEmail() {
        when(settings.findBySettingKey("timesheet.reminders.enabled"))
                .thenReturn(Optional.of(SystemSetting.builder().settingValue("false").build()));
        service.sendTimesheetReminders(Instant.parse("2026-09-18T17:00:00Z"));
        verifyNoInteractions(db, email, periods);
    }
}
