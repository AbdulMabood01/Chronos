ALTER TABLE project_assignments
    ADD COLUMN IF NOT EXISTS start_date DATE,
    ADD COLUMN IF NOT EXISTS end_date DATE;

CREATE INDEX IF NOT EXISTS idx_project_assignments_dates ON project_assignments(start_date, end_date);
