ALTER TYPE vacation_type_enum ADD VALUE IF NOT EXISTS 'SPECIAL';

ALTER TABLE vacation_requests ADD COLUMN special_reason VARCHAR(120);
ALTER TABLE vacation_requests ADD COLUMN accounting_type VARCHAR(32);
ALTER TABLE vacation_requests ADD CONSTRAINT vacation_accounting_type_check
    CHECK (accounting_type IS NULL OR accounting_type IN ('VACATION', 'SICK', 'BEREAVEMENT', 'PAID_NO_QUOTA', 'UNPAID'));

-- Preserve the balance treatment used for already approved legacy requests.
UPDATE vacation_requests SET accounting_type = CASE
    WHEN vacation_type = 'SICK' THEN 'SICK'
    WHEN vacation_type = 'BEREAVEMENT' THEN 'BEREAVEMENT'
    WHEN vacation_type = 'UNPAID_LEAVE' THEN 'UNPAID'
    ELSE 'VACATION' END
WHERE status IN ('APPROVED', 'LOCKED');

CREATE TABLE leave_policy_years (
    year INTEGER PRIMARY KEY CHECK (year BETWEEN 1900 AND 9998),
    vacation_days NUMERIC(8,2) NOT NULL CHECK (vacation_days >= 0),
    sick_days NUMERIC(8,2) NOT NULL CHECK (sick_days >= 0),
    bereavement_days NUMERIC(8,2) NOT NULL CHECK (bereavement_days >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE leave_allowances ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'OVERRIDE'
    CHECK (source IN ('POLICY', 'OVERRIDE'));
