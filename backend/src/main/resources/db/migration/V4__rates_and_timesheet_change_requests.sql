ALTER TYPE timesheet_status ADD VALUE IF NOT EXISTS 'CHANGE_REQUESTED';

ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'BILL_RATE_CHANGED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'TIMESHEET_CHANGE_REQUESTED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'SETTINGS_UPDATED';

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS admin_override_hourly_rate DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS rate_overridden_by_id BIGINT REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS rate_overridden_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS rate_override_reason VARCHAR(500);

ALTER TABLE timesheets
    ADD COLUMN IF NOT EXISTS bill_rate DECIMAL(10, 2),
    ADD COLUMN IF NOT EXISTS bill_rate_updated_at TIMESTAMP;

UPDATE timesheets ts
SET bill_rate = COALESCE(ts.approved_hourly_rate, u.hourly_rate),
    bill_rate_updated_at = COALESCE(ts.updated_at, CURRENT_TIMESTAMP)
FROM users u
WHERE ts.user_id = u.id
  AND ts.bill_rate IS NULL;
