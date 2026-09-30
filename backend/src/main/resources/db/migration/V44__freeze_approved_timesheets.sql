ALTER TABLE timesheets ADD COLUMN approval_frozen BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE timesheets SET approval_frozen = TRUE WHERE status IN ('APPROVED', 'LOCKED');
