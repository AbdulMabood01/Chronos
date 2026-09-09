ALTER TYPE user_role_enum ADD VALUE IF NOT EXISTS 'PROJECT_MANAGER';
ALTER TYPE user_role_enum ADD VALUE IF NOT EXISTS 'ADMIN';

ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'PROJECT_CREATED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'PROJECT_UPDATED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'PROJECT_ASSIGNED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'PROJECT_UNASSIGNED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'PROJECT_MANAGER_CHANGED';
ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'TIMESHEET_REMINDER_SENT';

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    project_manager_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_projects_code ON projects(code);
CREATE INDEX idx_projects_manager_id ON projects(project_manager_id);
CREATE INDEX idx_projects_is_active ON projects(is_active);

CREATE TABLE project_assignments (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    assigned_by_id BIGINT REFERENCES users(id),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, user_id)
);

CREATE INDEX idx_project_assignments_project_id ON project_assignments(project_id);
CREATE INDEX idx_project_assignments_user_id ON project_assignments(user_id);
CREATE INDEX idx_project_assignments_active ON project_assignments(is_active);

ALTER TABLE timesheets
    ADD COLUMN IF NOT EXISTS primary_project_id BIGINT REFERENCES projects(id),
    ADD COLUMN IF NOT EXISTS last_reminder_sent_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS initial_reminder_sent_at TIMESTAMP;

ALTER TABLE time_entries
    ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES projects(id);

CREATE INDEX IF NOT EXISTS idx_time_entries_project_id ON time_entries(project_id);
CREATE INDEX IF NOT EXISTS idx_timesheets_primary_project_id ON timesheets(primary_project_id);

CREATE TABLE time_entry_sessions (
    id BIGSERIAL PRIMARY KEY,
    time_entry_id BIGINT NOT NULL REFERENCES time_entries(id) ON DELETE CASCADE,
    login_time TIME NOT NULL,
    logout_time TIME NOT NULL,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_time_entry_sessions_entry_id ON time_entry_sessions(time_entry_id);

INSERT INTO system_settings (setting_key, setting_value) VALUES
    ('timesheet.reminders.enabled', 'true'),
    ('timesheet.reminders.initial_days_before_month_end', '7'),
    ('timesheet.reminders.final_working_week_daily', 'true'),
    ('timesheet.reminders.frequency', 'DAILY'),
    ('timesheet.reminders.notification_method', 'IN_APP')
ON CONFLICT (setting_key) DO NOTHING;
