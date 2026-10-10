ALTER TABLE company_memberships ADD COLUMN employment_locked boolean NOT NULL DEFAULT false;
UPDATE company_memberships SET employment_locked=true WHERE employment_version>0;
ALTER TABLE users ADD COLUMN profile_correction_open boolean NOT NULL DEFAULT false;
CREATE TABLE member_detail_corrections (
 id bigserial PRIMARY KEY, company_id bigint NOT NULL REFERENCES companies(id), target_user_id bigint NOT NULL REFERENCES users(id),
 requested_by bigint NOT NULL REFERENCES users(id), kind varchar(20) NOT NULL CHECK(kind IN ('PROFILE','EMPLOYMENT')),
 reason varchar(1000) NOT NULL, status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','REJECTED','COMPLETED')),
 decided_by bigint REFERENCES users(id), decision_reason varchar(1000), created_at timestamptz NOT NULL DEFAULT now(), decided_at timestamptz,
 completed_at timestamptz
);
CREATE UNIQUE INDEX member_detail_correction_pending ON member_detail_corrections(company_id,target_user_id,kind) WHERE status='PENDING';
