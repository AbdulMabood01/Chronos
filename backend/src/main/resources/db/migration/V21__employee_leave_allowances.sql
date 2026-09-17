ALTER TYPE audit_action ADD VALUE IF NOT EXISTS 'LEAVE_ALLOWANCE_UPDATED';

CREATE TABLE leave_allowances (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    leave_year INTEGER NOT NULL CHECK (leave_year BETWEEN 1900 AND 9998),
    vacation_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (vacation_days >= 0),
    sick_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (sick_days >= 0),
    extra_vacation_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (extra_vacation_days >= 0),
    extra_sick_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (extra_sick_days >= 0),
    UNIQUE (user_id, leave_year)
);
