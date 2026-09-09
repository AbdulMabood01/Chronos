CREATE TABLE timesheet_project_submissions (
    id BIGSERIAL PRIMARY KEY,
    timesheet_id BIGINT NOT NULL REFERENCES timesheets(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    status timesheet_status NOT NULL DEFAULT 'DRAFT',
    total_hours DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by_id BIGINT REFERENCES users(id),
    rejected_at TIMESTAMP,
    rejected_by_id BIGINT REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(timesheet_id, project_id)
);

CREATE INDEX idx_timesheet_project_submissions_timesheet_id ON timesheet_project_submissions(timesheet_id);
CREATE INDEX idx_timesheet_project_submissions_project_id ON timesheet_project_submissions(project_id);
CREATE INDEX idx_timesheet_project_submissions_status ON timesheet_project_submissions(status);

INSERT INTO timesheet_project_submissions (
    timesheet_id,
    project_id,
    status,
    total_hours,
    submitted_at,
    approved_at,
    approved_by_id,
    rejected_at,
    rejected_by_id,
    rejection_reason
)
SELECT
    ts.id,
    te.project_id,
    ts.status,
    COALESCE(SUM(te.hours), 0),
    ts.submitted_at,
    ts.approved_at,
    ts.approved_by_id,
    ts.rejected_at,
    ts.rejected_by_id,
    ts.rejection_reason
FROM timesheets ts
JOIN time_entries te ON te.timesheet_id = ts.id
WHERE te.project_id IS NOT NULL
GROUP BY ts.id, te.project_id, ts.status, ts.submitted_at, ts.approved_at, ts.approved_by_id,
    ts.rejected_at, ts.rejected_by_id, ts.rejection_reason;
