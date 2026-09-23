ALTER TABLE users ADD COLUMN timezone VARCHAR(100) NOT NULL DEFAULT 'America/Chicago';

DELETE FROM system_settings WHERE setting_key IN (
    'timesheet.reminders.initial_days_before_month_end',
    'timesheet.reminders.final_working_week_daily',
    'timesheet.reminders.frequency',
    'timesheet.reminders.notification_method'
);
