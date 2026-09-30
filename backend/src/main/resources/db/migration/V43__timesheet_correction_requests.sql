CREATE TABLE timesheet_correction_requests (
    id BIGSERIAL PRIMARY KEY,
    timesheet_id BIGINT NOT NULL REFERENCES timesheets(id),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED')),
    employee_comment VARCHAR(500) NOT NULL,
    admin_comment VARCHAR(500),
    decided_by_id BIGINT REFERENCES users(id),
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_pending_timesheet_correction
    ON timesheet_correction_requests(timesheet_id, project_id) WHERE status = 'PENDING';
CREATE INDEX idx_timesheet_correction_status ON timesheet_correction_requests(status, created_at DESC);
CREATE INDEX idx_timesheet_correction_user ON timesheet_correction_requests(user_id, created_at DESC);

ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'TIMESHEET_CORRECTION_REQUESTED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'TIMESHEET_CORRECTION_APPROVED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'TIMESHEET_CORRECTION_DECLINED';
