ALTER TABLE employee_reports ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE employee_reports r SET updated_at = COALESCE(
    (SELECT max(h.created_at) FROM employee_report_history h WHERE h.report_id=r.id
     AND h.action NOT IN ('VIEWED','ATTACHMENT_DOWNLOADED')), r.submitted_at);
ALTER TABLE employee_reports ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE employee_reports ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE employee_report_history ADD COLUMN status VARCHAR(30);
-- Recover the latest known workflow status at each historical event.
UPDATE employee_report_history h SET status = COALESCE((
    SELECT CASE WHEN p.action='SUBMITTED' THEN 'SUBMITTED'
                ELSE trim(split_part(p.action, '→', 2)) END
    FROM employee_report_history p WHERE p.report_id=h.report_id AND p.id<=h.id
      AND (p.action='SUBMITTED' OR p.action LIKE '%→%') ORDER BY p.id DESC LIMIT 1
), 'SUBMITTED');
ALTER TABLE employee_report_history ALTER COLUMN status SET NOT NULL;
ALTER TABLE employee_report_history ADD CONSTRAINT report_history_status CHECK
    (status IN ('SUBMITTED','UNDER_REVIEW','INVESTIGATION','RESOLVED','CLOSED'));
ALTER TABLE employee_report_attachments ADD COLUMN privacy_processed BOOLEAN NOT NULL DEFAULT false;
