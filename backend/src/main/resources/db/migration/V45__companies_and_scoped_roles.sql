ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'COMPANY_CREATED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'COMPANY_INVITED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'COMPANY_INVITE_ACCEPTED';

CREATE TABLE companies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    slug VARCHAR(80) NOT NULL UNIQUE,
    plan_tier VARCHAR(20) NOT NULL DEFAULT 'FREE'
        CHECK (plan_tier IN ('FREE', 'SINGLE', 'MULTIPLE', 'ENTERPRISE', 'CUSTOM')),
    project_limit INTEGER NOT NULL DEFAULT 3 CHECK (project_limit > 0),
    team_limit INTEGER NOT NULL DEFAULT 6 CHECK (team_limit > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Existing installations are test data, but keeping them in a dedicated company
-- lets the migration run against an existing development database.
INSERT INTO companies(name, slug) VALUES ('Legacy Chronos', 'legacy-chronos');

ALTER TABLE projects ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE projects ADD COLUMN owner_user_id BIGINT REFERENCES users(id);
UPDATE projects SET company_id = (SELECT id FROM companies WHERE slug = 'legacy-chronos');
ALTER TABLE projects ALTER COLUMN company_id SET NOT NULL;
CREATE INDEX projects_company_id_idx ON projects(company_id);
ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_code_key;
CREATE UNIQUE INDEX projects_company_code_unique_idx ON projects(company_id, lower(code));

CREATE TABLE role_definitions (
    role_key VARCHAR(40) PRIMARY KEY,
    label VARCHAR(80) NOT NULL,
    scope VARCHAR(12) NOT NULL CHECK (scope IN ('PLATFORM', 'COMPANY', 'PROJECT', 'BOTH')),
    is_system BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO role_definitions(role_key, label, scope) VALUES
    ('PLATFORM_ADMIN', 'Platform Admin', 'PLATFORM'),
    ('COMPANY_ADMIN', 'Company Admin', 'COMPANY'),
    ('PROJECT_ADMIN', 'Project Admin', 'BOTH'),
    ('PROJECT_MANAGER', 'Project Manager', 'PROJECT'),
    ('MODERATOR', 'Moderator', 'COMPANY'),
    ('USER', 'User', 'PROJECT');

CREATE TABLE company_memberships (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    joined_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    UNIQUE(company_id, user_id)
);
CREATE INDEX company_memberships_user_idx ON company_memberships(user_id, status);

CREATE TABLE project_memberships (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REMOVED')),
    joined_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    UNIQUE(project_id, user_id)
);
CREATE INDEX project_memberships_user_idx ON project_memberships(user_id, status);

CREATE TABLE role_assignments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_key VARCHAR(40) NOT NULL REFERENCES role_definitions(role_key),
    company_id BIGINT REFERENCES companies(id),
    project_id BIGINT REFERENCES projects(id),
    assigned_by_user_id BIGINT REFERENCES users(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    removed_at TIMESTAMPTZ,
    CHECK (project_id IS NULL OR company_id IS NOT NULL)
);
CREATE UNIQUE INDEX role_assignments_active_unique_idx ON role_assignments
    (user_id, role_key, company_id, project_id) NULLS NOT DISTINCT WHERE removed_at IS NULL;
CREATE INDEX role_assignments_lookup_idx ON role_assignments(user_id, role_key, company_id, project_id)
    WHERE removed_at IS NULL;

CREATE TABLE moderator_grants (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    project_id BIGINT NOT NULL REFERENCES projects(id),
    moderator_user_id BIGINT NOT NULL REFERENCES users(id),
    timesheets BOOLEAN NOT NULL DEFAULT FALSE,
    expenses BOOLEAN NOT NULL DEFAULT FALSE,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    granted_by_user_id BIGINT NOT NULL REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    CHECK (timesheets OR expenses),
    CHECK (starts_on <= ends_on)
);
CREATE INDEX moderator_grants_active_idx ON moderator_grants(moderator_user_id, project_id, starts_on, ends_on)
    WHERE revoked_at IS NULL;

CREATE TABLE company_invitations (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    project_id BIGINT REFERENCES projects(id),
    invitee_email VARCHAR(255) NOT NULL,
    role_key VARCHAR(40) NOT NULL REFERENCES role_definitions(role_key),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_by_user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    CHECK (expires_at > created_at)
);
CREATE INDEX company_invitations_pending_idx ON company_invitations(company_id, invitee_email, expires_at)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

-- Preserve the existing development accounts as members of the legacy company.
INSERT INTO company_memberships(company_id, user_id, status, joined_at)
SELECT c.id, u.id, 'ACTIVE', now() FROM companies c CROSS JOIN users u
WHERE c.slug = 'legacy-chronos';
INSERT INTO role_assignments(user_id, role_key)
SELECT id, 'PLATFORM_ADMIN' FROM users WHERE role::text = 'ADMIN';
INSERT INTO role_assignments(user_id, role_key, company_id)
SELECT u.id, 'PROJECT_ADMIN', c.id FROM users u CROSS JOIN companies c
WHERE u.role::text = 'PROJECT_ADMIN' AND c.slug = 'legacy-chronos';
INSERT INTO project_memberships(project_id, user_id, status, joined_at)
SELECT project_id, user_id, 'ACTIVE', now() FROM project_assignments;
INSERT INTO project_memberships(project_id, user_id, status, joined_at)
SELECT p.id, p.project_manager_id, 'ACTIVE', now() FROM projects p
WHERE p.project_manager_id IS NOT NULL ON CONFLICT DO NOTHING;
INSERT INTO project_memberships(project_id, user_id, status, joined_at)
SELECT p.id, u.id, 'ACTIVE', now() FROM projects p CROSS JOIN users u
WHERE u.role::text = 'PROJECT_ADMIN' ON CONFLICT DO NOTHING;
INSERT INTO role_assignments(user_id, role_key, company_id, project_id)
SELECT pa.user_id, 'USER', p.company_id, p.id FROM project_assignments pa
JOIN projects p ON p.id = pa.project_id;
INSERT INTO role_assignments(user_id, role_key, company_id, project_id)
SELECT p.project_manager_id, 'PROJECT_MANAGER', p.company_id, p.id FROM projects p
WHERE p.project_manager_id IS NOT NULL
ON CONFLICT DO NOTHING;
INSERT INTO role_assignments(user_id, role_key, company_id, project_id)
SELECT u.id, 'PROJECT_ADMIN', p.company_id, p.id FROM users u CROSS JOIN projects p
WHERE u.role::text = 'PROJECT_ADMIN'
ON CONFLICT DO NOTHING;
