ALTER TABLE timesheets ADD COLUMN company_id bigint REFERENCES companies(id);
UPDATE timesheets t SET company_id = COALESCE(
    (SELECT p.company_id FROM projects p WHERE p.id=t.primary_project_id),
    (SELECT p.company_id FROM time_entries e JOIN projects p ON p.id=e.project_id WHERE e.timesheet_id=t.id ORDER BY e.id LIMIT 1),
    (SELECT m.company_id FROM company_memberships m WHERE m.user_id=t.user_id AND m.status='ACTIVE' ORDER BY m.company_id LIMIT 1),
    (SELECT id FROM companies ORDER BY id LIMIT 1));
ALTER TABLE timesheets ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE timesheets DROP CONSTRAINT IF EXISTS timesheets_user_id_year_month_key;
ALTER TABLE timesheets ADD CONSTRAINT timesheets_company_period_unique UNIQUE(user_id,company_id,year,month);
CREATE INDEX timesheets_company_id_idx ON timesheets(company_id);
