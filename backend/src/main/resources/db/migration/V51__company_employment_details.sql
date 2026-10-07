ALTER TABLE company_memberships
    ADD COLUMN employee_id VARCHAR(50),
    ADD COLUMN job_title VARCHAR(120),
    ADD COLUMN joining_date DATE,
    ADD COLUMN employment_version BIGINT NOT NULL DEFAULT 0 CHECK (employment_version >= 0),
    ADD COLUMN employment_updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Preserve legacy values on every existing membership. Future memberships start
-- with their own blank employment details; the global account is not a template.
UPDATE company_memberships m
SET employee_id=u.employee_id, job_title=u.job_title, joining_date=u.joining_date
FROM users u WHERE u.id=m.user_id;

-- Employee identifiers are case-sensitive and unique within a company.
-- NULL permits a membership whose employee identifier has not been assigned yet.
ALTER TABLE company_memberships ADD CONSTRAINT company_employee_id_unique UNIQUE(company_id,employee_id);

COMMENT ON COLUMN users.employee_id IS 'Legacy account identifier; frozen for compatibility. Company employee IDs are on company_memberships.';
COMMENT ON COLUMN users.job_title IS 'Legacy employment snapshot; new employment edits use company_memberships.';
COMMENT ON COLUMN users.joining_date IS 'Legacy employment snapshot; new employment edits use company_memberships.';
