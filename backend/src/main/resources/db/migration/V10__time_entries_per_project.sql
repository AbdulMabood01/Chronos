ALTER TABLE time_entries DROP CONSTRAINT IF EXISTS time_entries_timesheet_id_entry_date_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_time_entries_timesheet_date_project
    ON time_entries(timesheet_id, entry_date, project_id)
    WHERE project_id IS NOT NULL;
