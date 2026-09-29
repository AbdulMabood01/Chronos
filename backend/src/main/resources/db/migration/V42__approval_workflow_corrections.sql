ALTER TABLE timesheet_project_submissions ADD COLUMN correction_until TIMESTAMP;
ALTER TABLE timesheet_project_submissions ADD COLUMN correction_planned_hours NUMERIC;

-- Give existing rejected submissions a one-time correction window after deployment.
UPDATE timesheet_project_submissions
SET correction_until = CURRENT_TIMESTAMP + INTERVAL '14 days',
    correction_planned_hours = GREATEST(COALESCE(total_hours, 0),
        COALESCE((SELECT a.planned_hours FROM project_assignments a
                  JOIN timesheets t ON t.id = timesheet_project_submissions.timesheet_id
                  WHERE a.project_id = timesheet_project_submissions.project_id
                    AND a.user_id = t.user_id), 0))
WHERE status = 'REJECTED';

ALTER TABLE letter_requests
    ADD COLUMN approved_full_name VARCHAR(200),
    ADD COLUMN approved_job_title VARCHAR(120),
    ADD COLUMN approved_employment_start_date DATE,
    ADD COLUMN review_note VARCHAR(500);

-- Preserve the content of letters that were approved before reviewed snapshots existed.
UPDATE letter_requests l
SET approved_full_name = COALESCE(l.requested_full_name, concat_ws(' ', u.first_name, u.last_name)),
    approved_job_title = COALESCE(l.requested_job_title, u.job_title),
    approved_employment_start_date = COALESCE(l.employment_start_date, u.joining_date)
FROM users u
WHERE l.user_id = u.id AND l.status IN ('APPROVED', 'LOCKED');
