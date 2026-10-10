-- Reopenings remain active until resubmission, rather than expiring after seven days.
ALTER TABLE timesheet_project_submissions
    ADD COLUMN correction_open boolean NOT NULL DEFAULT false;

UPDATE timesheet_project_submissions
SET correction_open = true
WHERE correction_until IS NOT NULL AND status IN ('DRAFT', 'REJECTED');

UPDATE timesheet_project_submissions SET correction_until = NULL;
-- Period reopenings already record their approval in opening_status.
UPDATE timesheet_approval_periods SET correction_until = NULL;
