CREATE TABLE email_alert_preferences (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    announcements BOOLEAN NOT NULL DEFAULT TRUE,
    timesheets BOOLEAN NOT NULL DEFAULT TRUE,
    vacation BOOLEAN NOT NULL DEFAULT TRUE,
    letters BOOLEAN NOT NULL DEFAULT TRUE,
    reports BOOLEAN NOT NULL DEFAULT TRUE,
    feedback BOOLEAN NOT NULL DEFAULT TRUE,
    performance BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE email_alert_outbox (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category VARCHAR(30) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    path VARCHAR(100) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);
CREATE INDEX email_alert_pending ON email_alert_outbox(available_at,id) WHERE completed_at IS NULL;

ALTER TABLE company_announcements ADD COLUMN emailed_version INTEGER NOT NULL DEFAULT -1;
-- Do not resend the existing announcement feed when deploying alerts.
UPDATE company_announcements SET emailed_version=version
WHERE status='PUBLISHED' AND publish_date <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date;
