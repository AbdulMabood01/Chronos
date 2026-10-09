ALTER TABLE companies ADD COLUMN is_suspended BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE companies ADD COLUMN suspension_reason VARCHAR(500);
ALTER TABLE companies ADD COLUMN platform_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN platform_access_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE platform_activity (
 id BIGSERIAL PRIMARY KEY,user_id BIGINT REFERENCES users(id),company_id BIGINT REFERENCES companies(id),
 target_user_id BIGINT REFERENCES users(id),action VARCHAR(60) NOT NULL,reason VARCHAR(500),created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE invitation_delivery (
 id BIGSERIAL PRIMARY KEY,company_invitation_id BIGINT REFERENCES company_invitations(id),
 employee_id BIGINT REFERENCES users(id),token_hash VARCHAR(64) NOT NULL,encrypted_token BYTEA,
 status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENT','FAILED','CANCELLED')),
 attempts INTEGER NOT NULL DEFAULT 0,available_at TIMESTAMPTZ NOT NULL DEFAULT now(),created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 sent_at TIMESTAMPTZ,last_error VARCHAR(120),
 CHECK((company_invitation_id IS NOT NULL AND employee_id IS NULL) OR (employee_id IS NOT NULL AND company_invitation_id IS NULL))
);
CREATE INDEX invitation_delivery_pending ON invitation_delivery(available_at,id) WHERE status='PENDING';
CREATE INDEX invitation_delivery_company ON invitation_delivery(company_invitation_id,id DESC);
CREATE INDEX invitation_delivery_employee ON invitation_delivery(employee_id,id DESC);
