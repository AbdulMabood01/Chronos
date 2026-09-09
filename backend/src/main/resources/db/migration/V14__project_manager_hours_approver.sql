ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS project_manager_hours_approver_id BIGINT REFERENCES users(id);

CREATE INDEX IF NOT EXISTS idx_projects_pm_hours_approver_id ON projects(project_manager_hours_approver_id);
