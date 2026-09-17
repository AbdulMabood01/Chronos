-- Project assignment rates and historical approved timesheet rates are retained.
ALTER TABLE users
    DROP COLUMN hourly_rate,
    DROP COLUMN admin_override_hourly_rate,
    DROP COLUMN rate_overridden_by_id,
    DROP COLUMN rate_overridden_at,
    DROP COLUMN rate_override_reason;

DELETE FROM system_settings WHERE setting_key = 'default_hourly_rate';
