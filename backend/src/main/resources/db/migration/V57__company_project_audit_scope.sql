ALTER TABLE audit_logs ADD COLUMN project_id BIGINT REFERENCES projects(id);
CREATE OR REPLACE FUNCTION scope_company_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.company_id IS NULL THEN
  CASE NEW.entity_type
   WHEN 'Company' THEN SELECT id INTO NEW.company_id FROM companies WHERE id=NEW.entity_id;
   WHEN 'CompanyMembership' THEN SELECT company_id INTO NEW.company_id FROM company_memberships WHERE id=NEW.entity_id;
   WHEN 'Project' THEN SELECT company_id INTO NEW.company_id FROM projects WHERE id=NEW.entity_id;
   WHEN 'Timesheet' THEN SELECT company_id INTO NEW.company_id FROM timesheets WHERE id=NEW.entity_id;
   WHEN 'VacationRequest' THEN SELECT company_id INTO NEW.company_id FROM vacation_requests WHERE id=NEW.entity_id;
   WHEN 'LetterRequest' THEN SELECT company_id INTO NEW.company_id FROM letter_requests WHERE id=NEW.entity_id;
   ELSE NULL;
  END CASE;
 END IF;
 CASE NEW.entity_type
  WHEN 'Project' THEN SELECT id INTO NEW.project_id FROM projects WHERE id=NEW.entity_id;
  WHEN 'TimesheetProjectSubmission' THEN SELECT project_id INTO NEW.project_id FROM timesheet_project_submissions WHERE id=NEW.entity_id;
  WHEN 'TimesheetApprovalPeriod' THEN SELECT project_id INTO NEW.project_id FROM timesheet_approval_periods WHERE id=NEW.entity_id;
  WHEN 'ProjectExpense' THEN SELECT project_id INTO NEW.project_id FROM project_expenses WHERE id=NEW.entity_id;
  ELSE NULL;
 END CASE;
 IF NEW.company_id IS NULL AND NEW.project_id IS NOT NULL THEN SELECT company_id INTO NEW.company_id FROM projects WHERE id=NEW.project_id; END IF;
 RETURN NEW;
END $$;
UPDATE audit_logs SET project_id=project_id;
