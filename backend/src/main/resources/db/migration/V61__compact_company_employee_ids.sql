-- Keep account keys unchanged; compact IDs belong only to company employment records.
CREATE TABLE company_employee_id_history (
 company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 previous_id VARCHAR(50), employee_id VARCHAR(50) NOT NULL, changed_at TIMESTAMP NOT NULL DEFAULT now()
);
WITH numbered AS (
 SELECT company_id,user_id,employee_id AS previous_id,
 row_number() OVER (PARTITION BY company_id ORDER BY user_id)::text AS new_id
 FROM company_memberships
)
INSERT INTO company_employee_id_history(company_id,user_id,previous_id,employee_id)
SELECT company_id,user_id,previous_id,new_id FROM numbered;
UPDATE company_memberships SET employee_id=NULL;
UPDATE company_memberships m SET employee_id=h.employee_id,employment_version=employment_version+1
FROM company_employee_id_history h WHERE h.company_id=m.company_id AND h.user_id=m.user_id;
CREATE FUNCTION assign_compact_employee_id() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.employee_id IS NULL OR NEW.employee_id !~ '^[1-9][0-9]{0,8}$' THEN
  PERFORM id FROM companies WHERE id=NEW.company_id FOR UPDATE;
  SELECT (COALESCE(max(employee_id::bigint),0)+1)::text INTO NEW.employee_id
  FROM company_memberships WHERE company_id=NEW.company_id;
 END IF;
 RETURN NEW;
END;
$$;
CREATE TRIGGER compact_employee_id BEFORE INSERT OR UPDATE OF employee_id ON company_memberships
FOR EACH ROW EXECUTE FUNCTION assign_compact_employee_id();
