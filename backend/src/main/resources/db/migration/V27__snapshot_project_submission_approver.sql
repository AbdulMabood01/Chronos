ALTER TABLE timesheet_project_submissions
    ADD COLUMN assigned_approver_id BIGINT REFERENCES users(id);

ALTER TABLE timesheet_project_submissions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE timesheet_project_submissions s
SET assigned_approver_id = CASE
    WHEN s.status IN ('APPROVED', 'LOCKED') THEN s.approved_by_id
    WHEN s.status = 'REJECTED' THEN s.rejected_by_id
    WHEN t.user_id = p.project_manager_id THEN p.project_manager_hours_approver_id
    ELSE p.project_manager_id END
FROM timesheets t, projects p
WHERE s.timesheet_id = t.id AND s.project_id = p.id;

CREATE INDEX idx_project_submission_assigned_approver ON timesheet_project_submissions(assigned_approver_id);
