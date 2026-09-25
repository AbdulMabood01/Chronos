-- Rename role labels without changing their permissions or existing assignments.
ALTER TYPE user_role_enum RENAME VALUE 'ADMIN' TO 'PROJECT_ADMIN';
ALTER TYPE user_role_enum RENAME VALUE 'SUPER_ADMIN' TO 'ADMIN';
