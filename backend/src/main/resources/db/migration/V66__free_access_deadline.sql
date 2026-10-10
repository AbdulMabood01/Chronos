-- Existing companies receive sixty days from deployment; new companies from creation.
ALTER TABLE companies ADD COLUMN free_started_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE companies ADD COLUMN free_ends_at timestamptz NOT NULL DEFAULT (now() + interval '60 days');
-- Legacy Free allowances must not bypass the new deadline.
UPDATE company_billing_terms SET ends_at = LEAST(COALESCE(ends_at, now() + interval '60 days'), now() + interval '60 days')
WHERE plan_key = 'FREE' AND status = 'ACTIVE';

ALTER TABLE company_billing_purchases ADD COLUMN terms_version varchar(40);
ALTER TABLE company_billing_purchases ADD COLUMN terms_accepted_at timestamptz;
ALTER TABLE company_billing_purchases ADD COLUMN terms_accepted_by bigint REFERENCES users(id);
