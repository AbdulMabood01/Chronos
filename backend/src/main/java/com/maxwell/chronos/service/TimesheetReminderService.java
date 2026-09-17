package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.SystemSettingRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
public class TimesheetReminderService {
    private final UserRepository userRepository;
    private final TimesheetRepository timesheetRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Scheduled(cron = "0 0 9 * * MON-FRI")
    @Transactional
    public void sendTimesheetReminders() {
        if (!booleanSetting("timesheet.reminders.enabled", true)) {
            return;
        }

        LocalDate today = LocalDate.now();
        if (isWeekend(today)) {
            return;
        }

        YearMonth period = YearMonth.from(today);
        LocalDate monthEnd = period.atEndOfMonth();
        int initialDays = intSetting("timesheet.reminders.initial_days_before_month_end", 7);
        boolean finalWeekDaily = booleanSetting("timesheet.reminders.final_working_week_daily", true);
        boolean isInitialReminderDay = today.equals(monthEnd.minusDays(initialDays));
        boolean isFinalWorkingWeek = finalWeekDaily && !today.isBefore(firstDayOfFinalWorkingWeek(monthEnd));

        if (!isInitialReminderDay && !isFinalWorkingWeek) {
            return;
        }

        userRepository.findByIsActiveTrue().stream()
                .filter(user -> !UserRole.SUPER_ADMIN.equals(user.getRole()))
                .forEach(user -> remindIfNeeded(user, period, today, isInitialReminderDay));
    }

    private void remindIfNeeded(User user, YearMonth period, LocalDate today, boolean initialReminderDay) {
        userRepository.findForUpdate(user.getId()).orElseThrow();
        Timesheet timesheet = timesheetRepository.findPeriodForUpdate(user.getId(), period.getYear(), period.getMonthValue())
                .orElseGet(() -> timesheetRepository.save(Timesheet.builder()
                        .user(user)
                        .year(period.getYear())
                        .month(period.getMonthValue())
                        .status(TimesheetStatus.DRAFT)
                        .billRate(java.math.BigDecimal.ZERO)
                        .billRateUpdatedAt(LocalDateTime.now())
                        .build()));

        if (!TimesheetStatus.DRAFT.equals(timesheet.getStatus()) && !TimesheetStatus.REJECTED.equals(timesheet.getStatus())) {
            return;
        }

        if (initialReminderDay && timesheet.getInitialReminderSentAt() != null) {
            return;
        }
        if (!initialReminderDay && timesheet.getLastReminderSentAt() != null
                && timesheet.getLastReminderSentAt().toLocalDate().equals(today)) {
            return;
        }

        notificationService.createNotification(user.getId(),
                "TIMESHEET_REMINDER",
                "Timesheet Reminder",
                "Please submit your " + period.getMonth() + " " + period.getYear() + " timesheet.",
                timesheet.getId(),
                "Timesheet");
        timesheet.setLastReminderSentAt(LocalDateTime.now());
        if (initialReminderDay) {
            timesheet.setInitialReminderSentAt(timesheet.getLastReminderSentAt());
        }
        timesheetRepository.save(timesheet);
        auditService.logAction(user.getId(), "TIMESHEET_REMINDER_SENT", "Timesheet", timesheet.getId(),
                "Reminder for " + period);
    }

    private LocalDate firstDayOfFinalWorkingWeek(LocalDate monthEnd) {
        LocalDate cursor = monthEnd;
        int workingDaysSeen = 0;
        while (workingDaysSeen < 5) {
            if (!isWeekend(cursor)) {
                workingDaysSeen++;
            }
            if (workingDaysSeen < 5) {
                cursor = cursor.minusDays(1);
            }
        }
        return cursor;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private boolean booleanSetting(String key, boolean fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
                .orElse(fallback);
    }

    private int intSetting(String key, int fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(setting -> {
                    try {
                        return Integer.parseInt(setting.getSettingValue());
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                })
                .orElse(fallback);
    }
}
