ALTER TABLE project_assignments
    ADD COLUMN IF NOT EXISTS bill_rate NUMERIC(10, 2);

UPDATE project_assignments pa
SET bill_rate = COALESCE(u.admin_override_hourly_rate, u.hourly_rate, 0)
FROM users u
WHERE pa.user_id = u.id
  AND pa.bill_rate IS NULL;

CREATE INDEX IF NOT EXISTS idx_project_assignments_bill_rate ON project_assignments(bill_rate);
