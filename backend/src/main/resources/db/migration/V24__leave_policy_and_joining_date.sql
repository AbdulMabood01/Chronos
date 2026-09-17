ALTER TABLE users ADD COLUMN joining_date DATE;
ALTER TABLE leave_allowances ADD COLUMN bereavement_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (bereavement_days >= 0);
INSERT INTO system_settings (setting_key, setting_value) VALUES
('vacation_days_per_year', '15'), ('sick_days_per_year', '5'), ('bereavement_days_per_year', '3')
ON CONFLICT (setting_key) DO NOTHING;
