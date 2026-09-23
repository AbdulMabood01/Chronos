CREATE TABLE company_announcements (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    publish_date DATE NOT NULL,
    expiration_date DATE,
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('NORMAL','IMPORTANT','URGENT')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    acknowledgment_required BOOLEAN NOT NULL DEFAULT FALSE,
    attachment_name VARCHAR(200),
    attachment_data BYTEA,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (expiration_date IS NULL OR expiration_date >= publish_date)
);
CREATE INDEX company_announcements_feed ON company_announcements(status,publish_date DESC);
CREATE TABLE announcement_receipts (
    announcement_id UUID NOT NULL REFERENCES company_announcements(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    viewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMPTZ,
    PRIMARY KEY (announcement_id,employee_id)
);
