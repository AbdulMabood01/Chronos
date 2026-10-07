-- Approval grants retain their original date windows and audit history.
INSERT INTO project_assignments(project_id,user_id,is_active,planned_hours,bill_rate,start_date,end_date,assigned_by_id,assigned_at)
SELECT project_id,moderator_user_id,true,0,0,min(starts_on),max(ends_on),min(granted_by_user_id),min(granted_at)
FROM moderator_grants WHERE revoked_at IS NULL GROUP BY project_id,moderator_user_id
ON CONFLICT(project_id,user_id) DO NOTHING;
INSERT INTO project_memberships(project_id,user_id,status,joined_at)
SELECT DISTINCT project_id,moderator_user_id,'ACTIVE',now() FROM moderator_grants WHERE revoked_at IS NULL
ON CONFLICT(project_id,user_id) DO NOTHING;
INSERT INTO role_assignments(company_id,project_id,user_id,role_key,assigned_by_user_id)
SELECT DISTINCT g.company_id,g.project_id,g.moderator_user_id,'USER',g.granted_by_user_id FROM moderator_grants g
WHERE g.revoked_at IS NULL AND NOT EXISTS(SELECT 1 FROM role_assignments r WHERE r.project_id=g.project_id AND r.user_id=g.moderator_user_id AND r.role_key='USER' AND r.removed_at IS NULL)
ON CONFLICT DO NOTHING;
UPDATE role_assignments SET removed_at=now() WHERE role_key='MODERATOR' AND removed_at IS NULL;
