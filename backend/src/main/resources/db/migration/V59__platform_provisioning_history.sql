-- Retain provisioning metadata without copying company/private audit details.
INSERT INTO platform_activity(user_id,company_id,action,created_at)
SELECT a.user_id,a.company_id,'COMPANY_CREATED',a.created_at
FROM audit_logs a WHERE a.action='COMPANY_CREATED' AND a.company_id IS NOT NULL
AND NOT EXISTS(SELECT 1 FROM platform_activity p WHERE p.company_id=a.company_id AND p.action='COMPANY_CREATED');
