-- Create ENUM types for statuses
CREATE TYPE timesheet_status AS ENUM ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'LOCKED');
CREATE TYPE vacation_status AS ENUM ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'LOCKED');
CREATE TYPE vacation_type_enum AS ENUM ('VACATION', 'SICK', 'PERSONAL', 'OTHER');
CREATE TYPE user_role_enum AS ENUM ('EMPLOYEE', 'SUPER_ADMIN');
CREATE TYPE audit_action AS ENUM (
    'USER_CREATED',
    'USER_DEACTIVATED',
    'USER_REACTIVATED',
    'ROLE_CHANGED',
    'HOURLY_RATE_CHANGED',
    'TIMESHEET_CREATED',
    'TIMESHEET_EDITED',
    'TIMESHEET_SUBMITTED',
    'TIMESHEET_REJECTED',
    'TIMESHEET_APPROVED',
    'TIMESHEET_REOPENED',
    'VACATION_CREATED',
    'VACATION_EDITED',
    'VACATION_SUBMITTED',
    'VACATION_REJECTED',
    'VACATION_APPROVED',
    'VACATION_REOPENED'
);

-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(50) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    role user_role_enum NOT NULL DEFAULT 'EMPLOYEE',
    hourly_rate DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT true,
    entra_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_entra_id ON users(entra_id);
CREATE INDEX idx_users_is_active ON users(is_active);

-- Timesheets table
CREATE TABLE timesheets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    status timesheet_status NOT NULL DEFAULT 'DRAFT',
    total_hours DECIMAL(8, 2),
    approved_hourly_rate DECIMAL(10, 2),
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by_id BIGINT REFERENCES users(id),
    rejected_at TIMESTAMP,
    rejected_by_id BIGINT REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, year, month)
);

CREATE INDEX idx_timesheets_user_id ON timesheets(user_id);
CREATE INDEX idx_timesheets_status ON timesheets(status);
CREATE INDEX idx_timesheets_year_month ON timesheets(year, month);

-- Time entries table (daily hours within a timesheet)
CREATE TABLE time_entries (
    id BIGSERIAL PRIMARY KEY,
    timesheet_id BIGINT NOT NULL REFERENCES timesheets(id) ON DELETE CASCADE,
    entry_date DATE NOT NULL,
    hours DECIMAL(5, 2) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(timesheet_id, entry_date)
);

CREATE INDEX idx_time_entries_timesheet_id ON time_entries(timesheet_id);
CREATE INDEX idx_time_entries_date ON time_entries(entry_date);

-- Vacation types lookup
CREATE TABLE vacation_types (
    id BIGSERIAL PRIMARY KEY,
    name vacation_type_enum NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Vacation requests table
CREATE TABLE vacation_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    vacation_type_id BIGINT NOT NULL REFERENCES vacation_types(id),
    hours DECIMAL(8, 2),
    status vacation_status NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500),
    submitted_at TIMESTAMP,
    approved_at TIMESTAMP,
    approved_by_id BIGINT REFERENCES users(id),
    rejected_at TIMESTAMP,
    rejected_by_id BIGINT REFERENCES users(id),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vacation_requests_user_id ON vacation_requests(user_id);
CREATE INDEX idx_vacation_requests_status ON vacation_requests(status);
CREATE INDEX idx_vacation_requests_dates ON vacation_requests(start_date, end_date);

-- Notifications table
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT false,
    entity_id BIGINT,
    entity_type VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_is_read ON notifications(is_read);

-- Audit logs table (immutable)
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    action audit_action NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT,
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- System settings table
CREATE TABLE system_settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default vacation types
INSERT INTO vacation_types (name, description) VALUES
    ('VACATION', 'Vacation/Paid Time Off'),
    ('SICK', 'Sick Leave'),
    ('PERSONAL', 'Personal Day'),
    ('OTHER', 'Other');
