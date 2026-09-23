ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);
-- Fail on case-insensitive duplicates so an operator can resolve ambiguous identities.
CREATE UNIQUE INDEX users_email_case_insensitive ON users (lower(email));
CREATE TABLE employee_invitations (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL UNIQUE REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);
