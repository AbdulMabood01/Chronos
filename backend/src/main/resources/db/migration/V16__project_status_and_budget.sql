ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS total_allocated_hours NUMERIC(10, 2);

UPDATE projects
SET status = CASE
    WHEN is_active = TRUE THEN 'ACTIVE'
    ELSE 'ARCHIVED'
END
WHERE status IS NULL;

ALTER TABLE projects
    ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_projects_status ON projects(status);
