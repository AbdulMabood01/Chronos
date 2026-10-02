CREATE TABLE company_access_requests (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    invited_at TIMESTAMPTZ,
    dismissed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX company_access_requests_pending_idx ON company_access_requests(company_id, email)
    WHERE invited_at IS NULL AND dismissed_at IS NULL;
