CREATE TABLE project_hour_plans (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    planned_hours DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    updated_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(project_id, user_id, year, month)
);

CREATE INDEX idx_project_hour_plans_project_period ON project_hour_plans(project_id, year, month);
CREATE INDEX idx_project_hour_plans_user_period ON project_hour_plans(user_id, year, month);
