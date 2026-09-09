-- VacationRequest entity stores vacation type as a direct enum column, not a FK to vacation_types
ALTER TABLE vacation_requests ADD COLUMN vacation_type vacation_type_enum;

UPDATE vacation_requests vr
SET vacation_type = vt.name
FROM vacation_types vt
WHERE vr.vacation_type_id = vt.id;

ALTER TABLE vacation_requests ALTER COLUMN vacation_type SET NOT NULL;
ALTER TABLE vacation_requests DROP COLUMN vacation_type_id;
