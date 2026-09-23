CREATE TABLE employee_feedback (
    id UUID PRIMARY KEY,
    sender_id BIGINT NOT NULL REFERENCES users(id),
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    content TEXT NOT NULL CHECK (length(trim(content)) BETWEEN 1 AND 20000),
    category VARCHAR(40),
    anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    sender_type VARCHAR(10) NOT NULL CHECK (sender_type IN ('Manager','Employee')),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (sender_id <> recipient_id)
);
CREATE INDEX feedback_received ON employee_feedback(recipient_id, submitted_at DESC);
CREATE INDEX feedback_given ON employee_feedback(sender_id, submitted_at DESC);

CREATE TABLE performance_reviews (
    id UUID PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES users(id),
    review_year INTEGER NOT NULL CHECK (review_year BETWEEN 1900 AND 9999),
    quarter INTEGER NOT NULL CHECK (quarter BETWEEN 1 AND 4),
    summary TEXT NOT NULL,
    accomplishments TEXT,
    strengths TEXT,
    improvements TEXT,
    goals TEXT,
    comments TEXT,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE(employee_id, review_year, quarter)
);
CREATE TABLE performance_review_audit (
    id BIGSERIAL PRIMARY KEY,
    review_id UUID NOT NULL REFERENCES performance_reviews(id),
    actor_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(20) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    snapshot JSONB NOT NULL
);
