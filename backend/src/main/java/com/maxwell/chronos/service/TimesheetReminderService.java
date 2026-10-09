package com.maxwell.chronos.service;

import com.maxwell.chronos.domain.User;
import com.maxwell.chronos.repository.SystemSettingRepository;
import com.maxwell.chronos.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetReminderService {
    static final String ELIGIBLE_ASSIGNMENTS = "SELECT a.user_id,a.project_id,p.code FROM project_assignments a " +
        "JOIN projects p ON p.id=a.project_id JOIN users u ON u.id=a.user_id " +
        "JOIN company_settings s ON s.company_id=p.company_id AND s.reminders_enabled=TRUE JOIN companies active_company ON active_company.id=p.company_id AND NOT active_company.is_suspended " +
        "JOIN company_memberships cm ON cm.company_id=p.company_id AND cm.user_id=u.id AND cm.status='ACTIVE' " +
        "JOIN project_memberships pm ON pm.project_id=p.id AND pm.user_id=u.id AND pm.status='ACTIVE' " +
        "WHERE a.is_active=TRUE AND p.is_active=TRUE AND u.is_active=TRUE AND u.admin_locked=FALSE " +
        "AND EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.company_id=p.company_id " +
        "AND r.project_id=p.id AND r.role_key IN ('USER','PROJECT_MANAGER') AND r.removed_at IS NULL) " +
        "AND NOT EXISTS(SELECT 1 FROM role_assignments r WHERE r.user_id=u.id AND r.removed_at IS NULL " +
        "AND ((r.role_key='PLATFORM_ADMIN' AND r.company_id IS NULL AND r.project_id IS NULL) " +
        "OR (r.role_key='PROJECT_ADMIN' AND r.company_id=p.company_id AND r.project_id=p.id)))";
    private final UserRepository users;
    private final SystemSettingRepository settings;
    private final JdbcTemplate db;
    private final TimesheetPeriodService periods;
    private final TimesheetReminderEmailService email;
    private final NotificationService notifications;
    private final TransactionTemplate transactions;

    @Scheduled(cron = "0 * * * * *", zone = "UTC")
    public void sendTimesheetReminders() { sendTimesheetReminders(Instant.now()); }

    void sendTimesheetReminders(Instant instant) {
        if (!settings.findBySettingKey(PlatformSettingsService.REMINDERS)
                .map(s -> Boolean.parseBoolean(s.getSettingValue())).orElse(true)) return;
        for (var assignment : db.queryForList(ELIGIBLE_ASSIGNMENTS)) {
            long userId=((Number)assignment.get("user_id")).longValue();
            long projectId=((Number)assignment.get("project_id")).longValue();
            try {
                transactions.executeWithoutResult(status -> remind(userId,projectId,(String)assignment.get("code"),instant));
            } catch (RuntimeException ex) {
                log.error("Timesheet email failed for employee {} project {}",userId,projectId,ex);
            }
        }
    }

    private void remind(long userId,long projectId,String projectCode,Instant instant) {
        User user=users.findForUpdate(userId).orElseThrow();
        // Recheck after acquiring the account lock: a queued candidate may have lost access or paused reminders.
        if(db.queryForList(ELIGIBLE_ASSIGNMENTS+" AND a.user_id=? AND a.project_id=?",userId,projectId).isEmpty())return;
        if (!settings.findBySettingKey(PlatformSettingsService.REMINDERS)
                .map(s -> Boolean.parseBoolean(s.getSettingValue())).orElse(true)) return;
        ZonedDateTime now=instant.atZone(ZoneId.of(user.getTimezone()));
        LocalDate today=now.toLocalDate();
        if (!now.toLocalTime().isBefore(LocalTime.of(17,0))) {
            var period=periods.period(projectId,today);
            if(period.end().equals(today) && periods.markReminder(userId,projectId,period,false)) {
                email.sendPeriodEmail(user,projectCode,period.start(),period.end(),false);
                notifications.createNotification(userId,"TIMESHEET_REMINDER","Timesheet due today",
                        projectCode+" timesheet due by 11:59pm",null,"TimesheetApprovalPeriod");
            }
        }
        if (!now.toLocalTime().isBefore(LocalTime.of(0,1))) {
            LocalDate yesterday=today.minusDays(1);
            var period=periods.period(projectId,yesterday);
            if(period.end().equals(yesterday) && periods.markReminder(userId,projectId,period,true)) {
                email.sendPeriodEmail(user,projectCode,period.start(),period.end(),true);
                notifications.createNotification(userId,"TIMESHEET_LATE","Timesheet submission late",
                        projectCode+" timesheet is late",null,"TimesheetApprovalPeriod");
            }
        }
    }
}
