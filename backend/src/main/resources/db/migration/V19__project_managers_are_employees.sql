-- Project relationships, not a global role, grant manager/approver permissions.
UPDATE users SET role = 'EMPLOYEE' WHERE role = 'PROJECT_MANAGER';

-- Keep the PostgreSQL enum label for compatibility with applied migrations,
-- but prevent old clients or application versions from storing it again.
ALTER TABLE users ADD CONSTRAINT users_no_project_manager_role
    CHECK (role <> 'PROJECT_MANAGER'::user_role_enum);
