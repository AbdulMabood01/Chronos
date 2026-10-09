CREATE TABLE company_settings (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    company_name VARCHAR(150) NOT NULL CHECK (length(trim(company_name)) > 0),
    vacation_days NUMERIC(5,2) NOT NULL DEFAULT 15 CHECK (vacation_days BETWEEN 0 AND 366),
    sick_days NUMERIC(5,2) NOT NULL DEFAULT 5 CHECK (sick_days BETWEEN 0 AND 366),
    bereavement_days NUMERIC(5,2) NOT NULL DEFAULT 3 CHECK (bereavement_days BETWEEN 0 AND 366),
    reminders_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Preserve policy defaults once. Identity comes from each company, never global company_name.
INSERT INTO company_settings(company_id,company_name,vacation_days,sick_days,bereavement_days,reminders_enabled)
SELECT c.id,c.name,
    COALESCE((SELECT CASE WHEN setting_value ~ '^[0-9]{1,3}(\.[0-9]{1,2})?$' THEN CASE WHEN setting_value::numeric <= 366 THEN setting_value::numeric ELSE 15 END ELSE 15 END FROM system_settings WHERE setting_key='vacation_days_per_year'),15),
    COALESCE((SELECT CASE WHEN setting_value ~ '^[0-9]{1,3}(\.[0-9]{1,2})?$' THEN CASE WHEN setting_value::numeric <= 366 THEN setting_value::numeric ELSE 5 END ELSE 5 END FROM system_settings WHERE setting_key='sick_days_per_year'),5),
    COALESCE((SELECT CASE WHEN setting_value ~ '^[0-9]{1,3}(\.[0-9]{1,2})?$' THEN CASE WHEN setting_value::numeric <= 366 THEN setting_value::numeric ELSE 3 END ELSE 3 END FROM system_settings WHERE setting_key='bereavement_days_per_year'),3),
    COALESCE((SELECT lower(setting_value) <> 'false' FROM system_settings WHERE setting_key='timesheet.reminders.enabled'),true)
FROM companies c;
-- Future companies receive independent defaults with no ongoing global policy fallback.
CREATE FUNCTION initialize_company_settings() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO company_settings(company_id,company_name) VALUES (NEW.id,NEW.name);
    RETURN NEW;
END;
$$;
CREATE TRIGGER initialize_company_settings AFTER INSERT ON companies
FOR EACH ROW EXECUTE FUNCTION initialize_company_settings();
INSERT INTO system_settings(setting_key,setting_value)
VALUES ('platform.timesheet.reminders.enabled',COALESCE((SELECT setting_value FROM system_settings WHERE setting_key='timesheet.reminders.enabled'),'true'))
ON CONFLICT (setting_key) DO NOTHING;
ALTER TABLE system_settings ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
COMMENT ON TABLE company_settings IS 'Company policy defaults. Leave allowance publication is a separate company workflow.';
