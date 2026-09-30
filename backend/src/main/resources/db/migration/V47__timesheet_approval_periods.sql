CREATE TABLE timesheet_approval_periods (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id),
    project_id bigint NOT NULL REFERENCES projects(id),
    company_id bigint NOT NULL REFERENCES companies(id),
    period_start date NOT NULL,
    period_end date NOT NULL,
    frequency varchar(10) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    submitted_at timestamptz,
    is_late boolean NOT NULL DEFAULT false,
    reminder_sent_at timestamptz,
    late_email_sent_at timestamptz,
    reviewed_at timestamptz,
    reviewed_by_id bigint REFERENCES users(id),
    approved_bill_rate numeric(10,2),
    review_comment varchar(500),
    fallback_reason varchar(500),
    correction_until timestamptz,
    opening_requested_at timestamptz,
    opening_reason varchar(500),
    opening_status varchar(20),
    opening_decision_comment varchar(500),
    UNIQUE (user_id, project_id, period_start, period_end),
    CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY')),
    CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),
    CHECK (period_end >= period_start)
);
CREATE INDEX timesheet_approval_periods_review_idx ON timesheet_approval_periods (status, project_id);
CREATE INDEX timesheet_approval_periods_employee_idx ON timesheet_approval_periods (user_id, project_id, period_start);

CREATE TABLE project_approval_frequency_changes (
    id bigserial PRIMARY KEY,
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    effective_on date NOT NULL,
    frequency varchar(10) NOT NULL CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY')),
    UNIQUE (project_id, effective_on)
);
