ALTER TABLE vacation_requests ADD COLUMN company_id BIGINT REFERENCES companies(id);
-- Only migrate records with an unambiguous owning membership. Retain ambiguous legacy records for explicit reconciliation.
UPDATE vacation_requests v SET company_id=m.company_id FROM (
    SELECT user_id,min(company_id) AS company_id FROM company_memberships WHERE status IN ('ACTIVE','REMOVED') GROUP BY user_id HAVING count(*)=1
) m WHERE v.user_id=m.user_id;
CREATE INDEX vacation_company_dates_idx ON vacation_requests(company_id,user_id,start_date,end_date);
CREATE TABLE company_leave_policies (
    company_id BIGINT NOT NULL REFERENCES companies(id),
    leave_year INTEGER NOT NULL CHECK (leave_year BETWEEN 1900 AND 9998),
    vacation_days NUMERIC(8,2) NOT NULL CHECK (vacation_days BETWEEN 0 AND 366),
    sick_days NUMERIC(8,2) NOT NULL CHECK (sick_days BETWEEN 0 AND 366),
    bereavement_days NUMERIC(8,2) NOT NULL CHECK (bereavement_days BETWEEN 0 AND 366),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,leave_year)
);
INSERT INTO company_leave_policies(company_id,leave_year,vacation_days,sick_days,bereavement_days)
SELECT c.id,p.year,p.vacation_days,p.sick_days,p.bereavement_days FROM companies c CROSS JOIN leave_policy_years p
WHERE p.vacation_days<=366 AND p.sick_days<=366 AND p.bereavement_days<=366;
CREATE TABLE company_leave_allowances (
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    leave_year INTEGER NOT NULL CHECK (leave_year BETWEEN 1900 AND 9998),
    vacation_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (vacation_days>=0),
    sick_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (sick_days>=0),
    bereavement_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (bereavement_days>=0),
    extra_vacation_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (extra_vacation_days>=0),
    extra_sick_days NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (extra_sick_days>=0),
    source VARCHAR(16) NOT NULL DEFAULT 'POLICY' CHECK(source IN ('POLICY','OVERRIDE')),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY(company_id,user_id,leave_year),
    FOREIGN KEY(company_id,user_id) REFERENCES company_memberships(company_id,user_id)
);
INSERT INTO company_leave_allowances(company_id,user_id,leave_year,vacation_days,sick_days,bereavement_days,extra_vacation_days,extra_sick_days,source)
SELECT m.company_id,a.user_id,a.leave_year,a.vacation_days,a.sick_days,a.bereavement_days,a.extra_vacation_days,a.extra_sick_days,a.source
FROM leave_allowances a JOIN (
    SELECT user_id,min(company_id) AS company_id FROM company_memberships WHERE status IN ('ACTIVE','REMOVED') GROUP BY user_id HAVING count(*)=1
) m ON m.user_id=a.user_id;
COMMENT ON COLUMN vacation_requests.company_id IS 'Null legacy ownership is retained for explicit reconciliation and is not visible through company APIs.';
