ALTER TABLE projects ADD COLUMN expense_budget numeric(14,2);
ALTER TABLE projects ADD CONSTRAINT projects_expense_budget_nonnegative CHECK (expense_budget IS NULL OR expense_budget >= 0);

CREATE TABLE project_expenses (
 id bigserial PRIMARY KEY,
 project_id bigint NOT NULL REFERENCES projects(id),
 employee_id bigint NOT NULL REFERENCES users(id),
 category varchar(30) NOT NULL CHECK (category IN ('TRAVEL','MEALS','SOFTWARE','EQUIPMENT','SUPPLIES','OTHER')),
 amount numeric(14,2) NOT NULL CHECK (amount > 0),
 expense_date date NOT NULL,
 description text NOT NULL,
 receipt_key uuid,
 receipt_name varchar(200),
 receipt_type varchar(100),
 receipt_size bigint,
 status varchar(30) NOT NULL CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED','CHANGES_REQUESTED')),
 submitted_at timestamptz NOT NULL DEFAULT now(),
 reviewer_id bigint REFERENCES users(id),
 reviewed_at timestamptz,
 reviewer_comments text,
 updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX project_expenses_project_status_idx ON project_expenses(project_id,status);
CREATE INDEX project_expenses_employee_idx ON project_expenses(employee_id);

CREATE TABLE project_expense_history (
 id bigserial PRIMARY KEY,
 expense_id bigint NOT NULL REFERENCES project_expenses(id),
 actor_id bigint NOT NULL REFERENCES users(id),
 status varchar(30) NOT NULL,
 comment text,
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX project_expense_history_expense_idx ON project_expense_history(expense_id,created_at);

CREATE TABLE project_expense_budget_history (
 id bigserial PRIMARY KEY,
 project_id bigint NOT NULL REFERENCES projects(id),
 actor_id bigint NOT NULL REFERENCES users(id),
 previous_budget numeric(14,2),
 new_budget numeric(14,2),
 changed_at timestamptz NOT NULL DEFAULT now()
);
