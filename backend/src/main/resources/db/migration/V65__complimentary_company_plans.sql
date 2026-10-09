-- Complimentary capacity has no payment history and may have no expiry.
ALTER TABLE company_billing_terms DROP CONSTRAINT company_billing_terms_source_check;
ALTER TABLE company_billing_terms ALTER COLUMN source TYPE VARCHAR(16);
ALTER TABLE company_billing_terms ADD CONSTRAINT company_billing_terms_source_check
 CHECK(source IN ('PAID','TRIAL','LEGACY','CONTRACT','COMPLIMENTARY'));
