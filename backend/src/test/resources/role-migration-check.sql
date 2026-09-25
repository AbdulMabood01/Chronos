-- Run with psql against a database containing the existing user_role_enum.
-- All writes target temporary tables and the transaction is rolled back.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL search_path = pg_temp, public;
CREATE TEMP TABLE users (id BIGINT PRIMARY KEY, role user_role_enum NOT NULL);
INSERT INTO users VALUES (1, 'PROJECT_MANAGER'), (2, 'EMPLOYEE'), (3, 'PROJECT_ADMIN'), (4, 'ADMIN');
CREATE TEMP TABLE project_role_links (
    project_manager_id BIGINT REFERENCES pg_temp.users(id),
    project_manager_hours_approver_id BIGINT REFERENCES pg_temp.users(id)
);
INSERT INTO project_role_links VALUES (1, 2);

\ir ../../main/resources/db/migration/V19__project_managers_are_employees.sql

DO $check$
BEGIN
    IF (SELECT role FROM pg_temp.users WHERE id = 1) <> 'EMPLOYEE'
       OR (SELECT count(*) FROM pg_temp.users WHERE role = 'EMPLOYEE') <> 2
       OR (SELECT role FROM pg_temp.users WHERE id = 3) <> 'PROJECT_ADMIN'
       OR (SELECT role FROM pg_temp.users WHERE id = 4) <> 'ADMIN' THEN
        RAISE EXCEPTION 'Role conversion failed';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_temp.project_role_links WHERE project_manager_id = 1 AND project_manager_hours_approver_id = 2) THEN
        RAISE EXCEPTION 'Project relationships changed';
    END IF;
    BEGIN
        UPDATE pg_temp.users SET role = 'PROJECT_MANAGER' WHERE id = 2;
        RAISE EXCEPTION 'Removed role was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
END
$check$;
ROLLBACK;
