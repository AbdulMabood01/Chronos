ALTER TABLE email_alert_outbox ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE email_alert_outbox ADD COLUMN resource_id UUID;
ALTER TABLE email_alert_outbox ADD COLUMN handler_only BOOLEAN NOT NULL DEFAULT false;
-- Legacy communications queued without a company cannot be delivered safely.
UPDATE email_alert_outbox SET completed_at=now() WHERE completed_at IS NULL AND category IN ('ANNOUNCEMENTS','REPORTS','FEEDBACK','PERFORMANCE');
