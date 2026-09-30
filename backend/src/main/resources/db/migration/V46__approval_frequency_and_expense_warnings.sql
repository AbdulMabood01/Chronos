ALTER TABLE projects ADD COLUMN approval_frequency varchar(10) NOT NULL DEFAULT 'MONTHLY';
ALTER TABLE projects ADD COLUMN pending_approval_frequency varchar(10);
ALTER TABLE projects ADD COLUMN approval_frequency_effective_on date;
ALTER TABLE projects ADD CONSTRAINT projects_approval_frequency_check CHECK (approval_frequency IN ('DAILY','WEEKLY','MONTHLY'));
ALTER TABLE projects ADD CONSTRAINT projects_pending_approval_frequency_check CHECK (pending_approval_frequency IS NULL OR pending_approval_frequency IN ('DAILY','WEEKLY','MONTHLY'));

ALTER TABLE project_expenses ADD COLUMN over_budget_at_submission boolean NOT NULL DEFAULT false;
