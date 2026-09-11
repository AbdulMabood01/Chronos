ALTER TABLE timesheet_project_submissions ADD COLUMN approved_bill_rate NUMERIC(10,2);
-- Only reuse a recorded historical rate when the monthly approval was for this project.
-- Other legacy approvals remain unknown; never backfill them from today's assignment rate.
UPDATE timesheet_project_submissions s SET approved_bill_rate = t.approved_hourly_rate
FROM timesheets t WHERE s.timesheet_id = t.id AND s.project_id = t.primary_project_id
AND s.status IN ('APPROVED', 'LOCKED') AND t.approved_hourly_rate IS NOT NULL;

ALTER TABLE vacation_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
