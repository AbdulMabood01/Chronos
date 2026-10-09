CREATE TABLE company_letter_settings (
 company_id BIGINT PRIMARY KEY REFERENCES companies(id),
 version BIGINT NOT NULL DEFAULT 0,
 configuration JSONB NOT NULL,
 updated_by BIGINT REFERENCES users(id),
 updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE letter_requests ADD COLUMN issued_text TEXT;
ALTER TABLE letter_requests ADD COLUMN issued_definition TEXT;
ALTER TABLE letter_requests ADD COLUMN issued_pdf BYTEA;
