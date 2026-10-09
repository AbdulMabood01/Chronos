-- Billing grants are separate from operational suspension and payment history.
ALTER TABLE companies DROP CONSTRAINT companies_plan_tier_check;
ALTER TABLE companies ADD CONSTRAINT companies_plan_tier_check CHECK
    (plan_tier IN ('FREE','SINGLE','MULTIPLE','ENTERPRISE','CUSTOM','PRO','PRO_PLUS','PRO_MAX'));
ALTER TABLE companies ALTER COLUMN project_limit SET DEFAULT 1;
ALTER TABLE companies ALTER COLUMN team_limit SET DEFAULT 7;
ALTER TABLE company_memberships ADD COLUMN workforce_enabled BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE company_memberships m SET workforce_enabled=FALSE
WHERE EXISTS (SELECT 1 FROM role_assignments r WHERE r.company_id=m.company_id AND r.user_id=m.user_id
  AND r.removed_at IS NULL AND r.role_key IN ('COMPANY_ADMIN','PROJECT_ADMIN'))
AND NOT EXISTS (SELECT 1 FROM role_assignments r WHERE r.company_id=m.company_id AND r.user_id=m.user_id
  AND r.removed_at IS NULL AND r.role_key IN ('USER','PROJECT_MANAGER'));

CREATE TABLE company_billing_profiles (
 company_id BIGINT PRIMARY KEY REFERENCES companies(id), revision BIGINT NOT NULL DEFAULT 0,
 trial_used BOOLEAN NOT NULL DEFAULT FALSE, legal_name VARCHAR(200), billing_email VARCHAR(255),
 billing_address VARCHAR(1000), next_plan VARCHAR(20), next_extra_seats INTEGER CHECK(next_extra_seats>=0)
);
INSERT INTO company_billing_profiles(company_id) SELECT id FROM companies;
CREATE TABLE company_billing_terms (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), plan_key VARCHAR(20) NOT NULL,
 source VARCHAR(12) NOT NULL CHECK(source IN ('PAID','TRIAL','LEGACY','CONTRACT')),
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','SUPERSEDED','REFUNDED','DISPUTED')),
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ,
 project_limit INTEGER NOT NULL CHECK(project_limit>0), included_users INTEGER NOT NULL CHECK(included_users>0),
 extra_seats INTEGER NOT NULL DEFAULT 0 CHECK(extra_seats>=0), term_months INTEGER NOT NULL DEFAULT 0,
 seat_price_cents BIGINT NOT NULL DEFAULT 0, catalog_version VARCHAR(40) NOT NULL,
 predecessor_id UUID REFERENCES company_billing_terms(id), created_by BIGINT REFERENCES users(id),
 reason VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(ends_at IS NULL OR ends_at>starts_at)
);
CREATE INDEX company_billing_terms_effective ON company_billing_terms(company_id,starts_at,ends_at) WHERE status='ACTIVE';
-- Preserve every pre-billing company's configured limits and existing access.
-- This is a legacy grant, never a fabricated paid transaction or renamed plan.
INSERT INTO company_billing_terms(id,company_id,plan_key,source,starts_at,project_limit,included_users,catalog_version,reason)
SELECT md5('chronos-legacy-billing-'||c.id)::uuid,c.id,c.plan_tier,'LEGACY',now(),
 GREATEST(c.project_limit,(SELECT count(*) FROM projects p WHERE p.company_id=c.id AND p.status NOT IN ('COMPLETED','ARCHIVED'))::integer),
 GREATEST(c.team_limit,(SELECT count(*) FROM company_memberships m WHERE m.company_id=c.id AND m.status='ACTIVE')::integer+
 (SELECT count(DISTINCT lower(i.invitee_email)) FROM company_invitations i WHERE i.company_id=c.id AND i.accepted_at IS NULL AND i.revoked_at IS NULL AND i.expires_at>now())::integer),
 'legacy-v64','Preserved pre-billing access; review before converting to a commercial plan' FROM companies c;

CREATE TABLE company_billing_purchases (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), actor_id BIGINT NOT NULL REFERENCES users(id),
 kind VARCHAR(12) NOT NULL CHECK(kind IN ('PLAN','SEATS')), plan_key VARCHAR(20) NOT NULL,
 term_months INTEGER NOT NULL, extra_seats INTEGER NOT NULL CHECK(extra_seats>=0),
 project_limit INTEGER NOT NULL, included_users INTEGER NOT NULL,
 amount_cents BIGINT NOT NULL CHECK(amount_cents>0), subtotal_cents BIGINT NOT NULL, discount_cents BIGINT NOT NULL,
 tax_cents BIGINT NOT NULL DEFAULT 0, currency VARCHAR(3) NOT NULL DEFAULT 'usd',
 seat_price_cents BIGINT NOT NULL, catalog_version VARCHAR(40) NOT NULL,
 revision BIGINT NOT NULL, predecessor_id UUID REFERENCES company_billing_terms(id),
 starts_at TIMESTAMPTZ NOT NULL, ends_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'QUOTED' CHECK(status IN ('QUOTED','CHECKOUT','FULFILLED','RECONCILIATION','REFUND_PENDING','REFUND_FAILED','REFUNDED','DISPUTED','CANCELED')),
 stripe_session VARCHAR(255) UNIQUE, payment_intent VARCHAR(255) UNIQUE, checkout_url TEXT,
 paid_at TIMESTAMPTZ, fulfilled_at TIMESTAMPTZ, term_id UUID REFERENCES company_billing_terms(id),
 refund_id VARCHAR(255) UNIQUE, refund_reason VARCHAR(500), refund_requested_by BIGINT REFERENCES users(id),
 billing_snapshot TEXT NOT NULL, failure_code VARCHAR(80), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK(ends_at>starts_at)
);
CREATE INDEX company_billing_purchases_company ON company_billing_purchases(company_id,created_at DESC);
CREATE TABLE company_billing_events (
 event_id VARCHAR(255) PRIMARY KEY, event_type VARCHAR(100) NOT NULL, payload TEXT NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(), processed_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0,
 failure_code VARCHAR(80)
);
CREATE TABLE company_billing_receipts (
 id UUID PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id),
 purchase_id UUID NOT NULL UNIQUE REFERENCES company_billing_purchases(id), pdf BYTEA NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), reissue_count INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE company_billing_activity (
 id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), actor_id BIGINT REFERENCES users(id),
 action VARCHAR(80) NOT NULL, reference VARCHAR(255), reason VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE company_billing_delivery (
 id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL REFERENCES companies(id), recipient_user_id BIGINT REFERENCES users(id),
 delivery_key VARCHAR(255) UNIQUE NOT NULL, subject VARCHAR(200) NOT NULL, message VARCHAR(1500) NOT NULL,
 sent_at TIMESTAMPTZ, attempts INTEGER NOT NULL DEFAULT 0, next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
