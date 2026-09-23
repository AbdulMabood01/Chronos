# Chronos Project Status Updates

These updates summarize the current codebase, including work in progress in the local workspace.

## Update 1: Application Foundation

- Established a Spring Boot backend and React frontend for Chronos.
- Organized backend logic into controllers, services, and repositories.
- Added PostgreSQL persistence with Flyway database migrations.
- Connected frontend screens to backend APIs through a shared API client.
- Documented application setup, architecture, and development workflows.

## Update 2: Authentication and Access

- Integrated Microsoft Entra ID authentication with JWT security.
- Defined employee, admin, and super admin access roles.
- Added protected frontend routes for role-specific workspaces.
- Implemented backend permission checks for project visibility and management.
- Added automated coverage for role permissions and account access.

## Update 3: Employee Profiles

- Added employee profile forms and profile completion prompts.
- Expanded employee records with contact and employment information.
- Included profile image support for employee accounts.
- Added timezone preferences to support local reminder scheduling.
- Included tests for profile permissions and frontend form behavior.

## Update 4: Project Onboarding

- Added project creation and editing with unique project codes.
- Required a project manager and a separate PM hours approver selection.
- Added employee assignments with start dates, end dates, and bill rates.
- Required positive assigned hours when onboarding project members.
- Added validation for duplicate codes, invalid dates, and negative rates.

## Update 5: Project Planning and Budgets

- Added planned hours for individual project assignments.
- Introduced monthly hour plans for more detailed resource planning.
- Added a project hours dashboard with employee-level information.
- Included project budget displays and total allocated hours.
- Prevented project completion or archiving while hours remain unfinalized.

## Update 6: Timesheet Workflows

- Added monthly timesheets with daily project-based time entries.
- Included a copy-previous-week component to simplify repeated entries.
- Added project-level timesheet submissions and approval tracking.
- Supported approval, rejection, and change-request workflows.
- Added tests covering timesheet behavior and project permissions.

## Update 7: Leave Management

- Added vacation requests with submission and review workflows.
- Expanded supported leave categories and employee leave allowances.
- Added leave balance panels and request balance previews.
- Included a team leave calendar for visibility into employee absences.
- Added tests for leave balances, request conflicts, and approval routing.

## Update 8: Notifications and Reminders

- Added an in-app notification center with read and unread tracking.
- Implemented timesheet reminder logic for missing, draft, and rejected submissions.
- Added Friday and month-end reminders based on employee timezones.
- Included email delivery support that requires deployment SMTP configuration.
- Documented reminder scheduling, retries, and delivery limitations.

## Update 9: Reporting and Employee Requests

- Added timesheet exports and monthly summary reporting.
- Included PDF generation for individual and project timesheets.
- Added vacation request exports for annual reporting.
- Implemented employee letter requests with approval and rejection handling.
- Added letter PDF generation and tests for report downloads and exports.

## Update 10: Administration and Quality Coverage

- Added administration screens for users, settings, and audit logs.
- Expanded the workspace with project, reporting, and approval screens.
- Added offboarding checks to handle pending hours and manager replacement.
- Included backend and frontend tests for key workflows and access rules.
- Test execution and deployment readiness were not verified for this status summary.

## Update 11: Confidential Employee Reports

- Added employee Reports navigation and a confidential incident form with categories, optional incident details, and supporting attachments.
- Added anonymous submission with a pre-submission privacy disclosure and unique report reference; anonymous records contain no account link.
- Added a Super Admin-only Employee Reports page with category, status, and submission-date filters.
- Added sequential review statuses, confidential notes, actions taken, resolution details, and private view/download/change audit history.
- Enforced current database-role checks for report access and attachment downloads; project management permissions do not grant access.

### Super Admin Reports Management

- Routed Super Admin Reports to employee incident management and moved time/leave exports to their own page.
- Added Report ID search, anonymous/identified filters, reporter and last-updated columns, and automatic refresh for new submissions.
- Added a chronological Report Tracker with status, internal notes, administrator, and timestamp on each entry.
- Removed original filenames and hidden metadata from supported anonymous attachments; older unsafe formats are withheld from HR downloads.
- Verified report authorization, PostgreSQL submission/retrieval and tracker workflows, attachment metadata removal, frontend behavior, and desktop/mobile browser navigation.

## Feedback & Performance Reviews

- Added a dedicated navigation section with separate Feedback and Performance Reviews pages.
- Added employee name/email search, optional categories, anonymous submissions, received feedback filters, and sender history.
- Protected anonymous sender identity in recipient API responses while retaining private sender history.
- Added Super Admin-only quarterly review creation, editing, publication, and audit snapshots, with one review per employee and quarter.
- Added read-only employee review history with year/quarter selection and the latest published review selected by default.
- Added backend permission and PostgreSQL lifecycle tests, frontend workflow tests, and a desktop/mobile browser check.

## Company Announcements

- Added Super Admin-only creation, editing, publishing, archiving, and deletion, enforced against the current database role.
- Added titles, messages, publish/expiration dates, Normal/Important/Urgent priorities, optional attachments up to 5 MB, and Draft/Published/Archived states.
- Added a prominent Overview panel and employee Announcements page with unread indicators, acknowledgment controls, and protected attachment downloads.
- Added unique employee views and acknowledgments, engagement counts, and a filter showing active employees who have not acknowledged an announcement.
- Publication dates use UTC; expiration dates are inclusive. Scheduled, expired, draft, and archived announcements are hidden from employee access.
- Editing resets tracking for the revised content; publishing and archiving preserve it. Version checks reject stale edits and acknowledgments.
- Added migration V32. Verified 5 backend authorization/database tests, 28 frontend workflow/navigation tests, 2 desktop/mobile browser tests, and the production frontend build.

## Project Health

- Added derived Healthy, Attention Needed, and At Risk evaluations with explicit reasons for hour-budget usage, resource-plan overruns, assignment-window timing, staffing gaps, overlapping resource allocations, missing submissions, and pending approvals.
- Added a responsive Project Health card throughout project details and an admin attention overview with direct project navigation.
- Used lifetime recorded hours independently of the reporting month; completed-month submission checks avoid flagging current-month drafts as overdue.
- Disclosed capacity assumptions and unavailable expense budgets, monetary budgets, milestones, and project deadlines instead of creating duplicate manual fields.
- Verified 26 backend tests, 21 frontend tests, the production build, and desktop/mobile/dark-theme browser behavior including health-error recovery.

## CI Failure Fixes (September 23, 2026)

- Updated profile test selectors to use the person-specific accessible button name.
- Corrected the team calendar test to distinguish project management permission from hours-review permission, retaining denial for reviewers who are not managers.
- Split employee copy/resubmit and manager project-favorites browser coverage, and fixed the fixture date so copy-week options remain deterministic.
- Upgraded Spring Boot to 3.5.16, Tomcat to 10.1.60, PostgreSQL JDBC to 42.7.12, Apache POI to 5.5.1, and PDFBox to 2.0.37. Removed the unused frontend xlsx dependency and its transitive packages.
- Verified all 179 backend tests, including the CI database integration tests against an isolated PostgreSQL database; all 134 frontend tests; 21 browser scenarios across the full run and focused rerun; and the production frontend build.
- Verified both CI security scans with Trivy 0.70.0: no HIGH/CRITICAL findings in tracked source/dependencies or the rebuilt backend JAR. Local reports are in backend/target/ci-trivy-results.json and backend/target/ci-trivy-packaged-results.json.
- Changes are local; a new GitHub Actions run is needed after pushing to confirm the hosted pipeline.
