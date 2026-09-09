ALTER TABLE project_assignments
    ADD COLUMN IF NOT EXISTS planned_hours DECIMAL(8, 2);
