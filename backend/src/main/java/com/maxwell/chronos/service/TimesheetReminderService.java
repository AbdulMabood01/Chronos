package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.Timesheet;
import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.enums.TimesheetStatus;
import com.maxwell.chronos.enums.UserRole;
import com.maxwell.chronos.repository.SystemSettingRepository;
import com.maxwell.chronos.repository.TimesheetRepository;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetReminderService {
    private final UserRepository userRepository;
    private final TimesheetRepository timesheetRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final TimesheetReminderEmailService emailService;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 */5 * * * *", zone = "UTC")
    public void sendTimesheetReminders() {
        sendTimesheetReminders(Instant.now());
    }

    void sendTimesheetReminders(Instant instant) {
        if (!booleanSetting("timesheet.reminders.enabled", true)) {
            return;
        }

        userRepository.findByIsActiveTrue().stream()
                .filter(user -> !UserRole.SUPER_ADMIN.equals(user.getRole()))
                .forEach(user -> {
                    try {
                        transactionTemplate.executeWithoutResult(status -> remindIfNeeded(user, instant));
                    } catch (RuntimeException e) {
                        log.error("Timesheet reminder failed for user {}", user.getId(), e);
                    }
                });
    }

    private void remindIfNeeded(User candidate, Instant instant) {
        User user = userRepository.findForUpdate(candidate.getId()).orElseThrow();
        if (!Boolean.TRUE.equals(user.getIsActive()) || UserRole.SUPER_ADMIN.equals(user.getRole())) {
            return;
        }
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneId.of(user.getTimezone()));
        LocalDate today = now.toLocalDate();
        YearMonth period = YearMonth.from(today);
        // Retry failures and catch up after short outages until the employee's midnight.
        if (now.getHour() < 20 || (today.getDayOfWeek() != DayOfWeek.FRIDAY
                && !today.equals(period.atEndOfMonth()))) {
            return;
        }
        Timesheet timesheet = timesheetRepository.findPeriodForUpdate(user.getId(), period.getYear(), period.getMonthValue())
                .orElseGet(() -> timesheetRepository.save(Timesheet.builder()
                        .user(user)
                        .year(period.getYear())
                        .month(period.getMonthValue())
                        .status(TimesheetStatus.DRAFT)
                        .billRate(java.math.BigDecimal.ZERO)
                        .billRateUpdatedAt(now)
                        .build()));

        if (!TimesheetStatus.DRAFT.equals(timesheet.getStatus()) && !TimesheetStatus.REJECTED.equals(timesheet.getStatus())) {
            return;
        }

        if (timesheet.getLastReminderSentAt() != null
                && timesheet.getLastReminderSentAt().toLocalDate().equals(now.toLocalDate())) {
            return;
        }

        emailService.sendReminder(user, period);
        notificationService.createNotification(user.getId(),
                "TIMESHEET_REMINDER",
                "Timesheet Reminder",
                "Please submit your " + period.getMonth() + " " + period.getYear() + " timesheet.",
                timesheet.getId(),
                "Timesheet");
        timesheet.setLastReminderSentAt(now);
        if (timesheet.getInitialReminderSentAt() == null) {
            timesheet.setInitialReminderSentAt(timesheet.getLastReminderSentAt());
        }
        timesheetRepository.save(timesheet);
        auditService.logAction(user.getId(), "TIMESHEET_REMINDER_SENT", "Timesheet", timesheet.getId(),
                "Reminder for " + period);
    }

    private boolean booleanSetting(String key, boolean fallback) {
        return systemSettingRepository.findBySettingKey(key)
                .map(setting -> Boolean.parseBoolean(setting.getSettingValue()))
                .orElse(fallback);
    }

}
