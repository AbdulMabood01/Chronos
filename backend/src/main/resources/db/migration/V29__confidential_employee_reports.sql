CREATE TABLE employee_reports (
    id UUID PRIMARY KEY,
    category VARCHAR(50) NOT NULL CHECK (category IN ('SEXUAL_HARASSMENT','WORKPLACE_HARASSMENT','DISCRIMINATION','SAFETY_CONCERN','WORKPLACE_MISCONDUCT','OTHER_INCIDENT')),
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    incident_at TIMESTAMP,
    location VARCHAR(500),
    people_involved TEXT,
    witnesses TEXT,
    anonymous BOOLEAN NOT NULL,
    reporter_id BIGINT REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED','UNDER_REVIEW','INVESTIGATION','RESOLVED','CLOSED')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((anonymous AND reporter_id IS NULL) OR (NOT anonymous AND reporter_id IS NOT NULL))
);
CREATE INDEX employee_reports_submission_idx ON employee_reports(submitted_at DESC);
CREATE TABLE employee_report_attachments (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES employee_reports(id),
    filename VARCHAR(200) NOT NULL,
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 10485760)
);
-- Kept out of the general audit feed and all employee/project exports.
CREATE TABLE employee_report_history (
    id BIGSERIAL PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES employee_reports(id),
    actor_id BIGINT REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    note TEXT,
    actions_taken TEXT,
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
