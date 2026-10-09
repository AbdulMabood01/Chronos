-- Retain ambiguous legacy records without guessing a company or granting access.
ALTER TABLE company_announcements ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE employee_feedback ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE performance_reviews ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE employee_reports ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE employee_reports ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE employee_reports ADD COLUMN recusal_salt BYTEA NOT NULL DEFAULT decode(replace(gen_random_uuid()::text,'-',''),'hex');
ALTER TABLE letter_requests ADD COLUMN company_id BIGINT REFERENCES companies(id);
ALTER TABLE letter_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
WITH single_member AS (SELECT user_id,min(company_id) company_id FROM company_memberships WHERE status IN ('ACTIVE','REMOVED') GROUP BY user_id HAVING count(*)=1)
UPDATE letter_requests r SET company_id=m.company_id FROM single_member m WHERE m.user_id=r.user_id;
WITH single_member AS (SELECT user_id,min(company_id) company_id FROM company_memberships WHERE status IN ('ACTIVE','REMOVED') GROUP BY user_id HAVING count(*)=1)
UPDATE employee_reports r SET company_id=m.company_id FROM single_member m WHERE m.user_id=r.reporter_id AND NOT r.anonymous;
WITH single_member AS (SELECT user_id,min(company_id) company_id FROM company_memberships WHERE status IN ('ACTIVE','REMOVED') GROUP BY user_id HAVING count(*)=1)
UPDATE company_announcements r SET company_id=m.company_id FROM single_member m WHERE m.user_id=r.created_by;
WITH shared AS (SELECT f.id,min(a.company_id) company_id FROM employee_feedback f JOIN company_memberships a ON a.user_id=f.sender_id JOIN company_memberships b ON b.company_id=a.company_id AND b.user_id=f.recipient_id WHERE a.status IN ('ACTIVE','REMOVED') AND b.status IN ('ACTIVE','REMOVED') GROUP BY f.id HAVING count(*)=1)
UPDATE employee_feedback f SET company_id=s.company_id FROM shared s WHERE s.id=f.id;
WITH shared AS (SELECT r.id,min(a.company_id) company_id FROM performance_reviews r JOIN company_memberships a ON a.user_id=r.created_by JOIN company_memberships b ON b.company_id=a.company_id AND b.user_id=r.employee_id WHERE a.status IN ('ACTIVE','REMOVED') AND b.status IN ('ACTIVE','REMOVED') GROUP BY r.id HAVING count(*)=1)
UPDATE performance_reviews r SET company_id=s.company_id FROM shared s WHERE s.id=r.id;
ALTER TABLE performance_reviews DROP CONSTRAINT performance_reviews_employee_id_review_year_quarter_key;
CREATE UNIQUE INDEX company_performance_review_period ON performance_reviews(company_id,employee_id,review_year,quarter);
CREATE INDEX announcements_company ON company_announcements(company_id,publish_date DESC);
CREATE INDEX feedback_company ON employee_feedback(company_id,recipient_id,submitted_at DESC);
CREATE INDEX reviews_company ON performance_reviews(company_id,employee_id);
CREATE INDEX reports_company ON employee_reports(company_id,submitted_at DESC);
CREATE INDEX letters_company ON letter_requests(company_id,user_id,status);

CREATE TABLE company_sensitive_grants (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), user_id BIGINT NOT NULL,
 permission VARCHAR(40) NOT NULL CHECK(permission IN ('PERFORMANCE_REVIEW','CONFIDENTIAL_HANDLER')),
 subject_user_id BIGINT, starts_on DATE NOT NULL, ends_on DATE NOT NULL CHECK(ends_on>=starts_on),
 purpose VARCHAR(500) NOT NULL CHECK(length(trim(purpose))>0), granted_by BIGINT NOT NULL REFERENCES users(id),
 granted_at TIMESTAMPTZ NOT NULL DEFAULT now(), revoked_at TIMESTAMPTZ, revoked_by BIGINT REFERENCES users(id), version BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(company_id,user_id) REFERENCES company_memberships(company_id,user_id),
 FOREIGN KEY(company_id,subject_user_id) REFERENCES company_memberships(company_id,user_id),
 CHECK((permission='PERFORMANCE_REVIEW' AND subject_user_id IS NOT NULL AND user_id<>subject_user_id) OR (permission='CONFIDENTIAL_HANDLER' AND subject_user_id IS NULL))
);
CREATE INDEX sensitive_grants_current ON company_sensitive_grants(company_id,user_id,permission) WHERE revoked_at IS NULL;
-- Keyed, case-specific exclusion tokens support recusal without storing anonymous reporter identity.
CREATE TABLE employee_report_exclusions (report_id UUID NOT NULL REFERENCES employee_reports(id), exclusion_token VARCHAR(64) NOT NULL, PRIMARY KEY(report_id,exclusion_token));

CREATE TABLE company_activity (
 id BIGSERIAL PRIMARY KEY,company_id BIGINT NOT NULL REFERENCES companies(id),user_id BIGINT REFERENCES users(id),
 action VARCHAR(80) NOT NULL,entity_type VARCHAR(80) NOT NULL,entity_id VARCHAR(80),created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX company_activity_feed ON company_activity(company_id,created_at DESC,id);
ALTER TABLE audit_logs ADD COLUMN company_id BIGINT REFERENCES companies(id);
CREATE FUNCTION scope_company_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.company_id IS NULL THEN
  CASE NEW.entity_type
   WHEN 'Company' THEN SELECT id INTO NEW.company_id FROM companies WHERE id=NEW.entity_id;
   WHEN 'CompanyMembership' THEN SELECT company_id INTO NEW.company_id FROM company_memberships WHERE id=NEW.entity_id;
   WHEN 'Project' THEN SELECT company_id INTO NEW.company_id FROM projects WHERE id=NEW.entity_id;
   WHEN 'Timesheet' THEN SELECT company_id INTO NEW.company_id FROM timesheets WHERE id=NEW.entity_id;
   WHEN 'VacationRequest' THEN SELECT company_id INTO NEW.company_id FROM vacation_requests WHERE id=NEW.entity_id;
   WHEN 'LetterRequest' THEN SELECT company_id INTO NEW.company_id FROM letter_requests WHERE id=NEW.entity_id;
   WHEN 'TimesheetApprovalPeriod' THEN SELECT p.company_id INTO NEW.company_id FROM timesheet_approval_periods a JOIN projects p ON p.id=a.project_id WHERE a.id=NEW.entity_id;
   WHEN 'ProjectExpense' THEN SELECT p.company_id INTO NEW.company_id FROM project_expenses e JOIN projects p ON p.id=e.project_id WHERE e.id=NEW.entity_id;
   ELSE NULL;
  END CASE;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER company_audit_scope BEFORE INSERT OR UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION scope_company_audit();
UPDATE audit_logs SET company_id=company_id;
CREATE INDEX company_audit_feed ON audit_logs(company_id,created_at DESC);
