CREATE TYPE letter_request_type AS ENUM ('EMPLOYMENT_VERIFICATION', 'IMMIGRATION', 'VACATION');

ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'LETTER_REQUEST_SUBMITTED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'LETTER_REQUEST_APPROVED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'LETTER_REQUEST_REJECTED';

CREATE TABLE letter_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    request_type letter_request_type NOT NULL,
    status vacation_status NOT NULL DEFAULT 'SUBMITTED',
    recipient_organization VARCHAR(255),
    recipient_address VARCHAR(500),
    purpose VARCHAR(255),
    immigration_case_type VARCHAR(120),
    destination_country VARCHAR(120),
    consulate_name VARCHAR(255),
    effective_date DATE,
    vacation_start_date DATE,
    vacation_end_date DATE,
    notes VARCHAR(1000),
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by_id BIGINT REFERENCES users(id),
    rejected_at TIMESTAMP,
    rejected_by_id BIGINT REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_letter_requests_user_id ON letter_requests(user_id);
CREATE INDEX idx_letter_requests_status ON letter_requests(status);
CREATE INDEX idx_letter_requests_type ON letter_requests(request_type);
