ALTER TABLE employee_report_history ADD COLUMN employee_visible BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX employee_reports_reporter_idx ON employee_reports(reporter_id, submitted_at DESC) WHERE NOT anonymous;
