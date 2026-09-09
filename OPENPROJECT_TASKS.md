# Chronos OpenProject Task Board

Prepared on: 2026-09-09

## Status Legend

- Completed: Implemented and build-verified.
- Ongoing: In progress or needs validation in real usage.
- Next 2 Months: Recommended work to close by 2026-11-09.

## Access, Roles, And Permissions

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Configure initial Super Admin email | Completed | Done | `superadmin@maxwellnetwork.org` is configured as the initial Super Admin. |
| Move User Management to Super Admin only | Completed | Done | Admins no longer see or access user management controls. |
| Allow Admin and Super Admin to view reports | Completed | Done | Reports page permission mismatch was fixed. |
| Keep Admin separate from Super Admin | Completed | Done | Admin role no longer automatically receives Super Admin privileges. |
| Remove admin user-level bill-rate override UI | Completed | Done | Rates now belong to project assignments instead of global user overrides. |
| Review backend permission tests for all role boundaries | Ongoing | 2026-09-30 | Add/expand tests for self-promotion, self-approval, project-specific approvals, and restricted user management. |

## Projects And Assignments

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Create/manage project codes | Completed | Done | Admin/Super Admin can create and manage projects. |
| Require Project Manager during project creation | Completed | Done | Project cannot be saved without PM. |
| Require PM-hours approver during project creation | Completed | Done | Prevents Project Manager self-approval. |
| Replace project side list with searchable project selector | Completed | Done | Project view starts blank and opens only after selecting/searching. |
| Add archived/completed project visibility | Completed | Done | Project filters include Active, All, Completed, Archived, On Hold, Draft, and setup states. |
| Add assignment start date, end date, and bill rate | Completed | Done | Required when assigning employees to projects. |
| Make assigned-project response employee-specific | Completed | Done | Employee timesheet only receives that employee's assignment details. |
| Make assigned projects period-aware | Completed | Done | Planned hours now reflect the selected timesheet month. |
| Improve project overview dashboard | Completed | Done | Overview reduced to budget, planned, logged, approved, alerts, and routing. |
| Add visual project analytics | Completed | Done | Project page includes chart-style hours visualization. |
| Improve project setup flow validation messages | Ongoing | 2026-09-25 | Make failed save/assignment errors more specific using backend response messages. |

## Timesheets

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Lock submitted project timesheets | Completed | Done | Submitted timesheets are read-only for employees until rejected. |
| Re-enable rejected timesheets for correction | Completed | Done | Rejected project timesheets become editable again. |
| Keep approved timesheets read-only | Completed | Done | Approved project timesheets remain locked for employees. |
| Add project selector beside month | Completed | Done | Employee selects project at top and sees that project only. |
| Blank view when employee has no assigned projects | Completed | Done | Timesheet page shows no project view if nothing is assigned. |
| Add clock icon for login/logout sessions | Completed | Done | Add-time button and project code were removed from day cells. |
| Support multiple login/logout sessions per day | Completed | Done | Daily hours can be calculated from multiple sessions. |
| Calculate daily hours from login/logout sessions | Completed | Done | Session totals update the day entry hours. |
| Use project assignment bill rate in timesheet | Completed | Done | Rate is project-specific for the employee/project combination. |
| Remove editable timesheet bill-rate override | Completed | Done | Timesheet rate is shown read-only. |
| Show planned and remaining hours in timesheet | Completed | Done | Timesheet displays logged/planned and hours remaining. |
| Update remaining hours in realtime | Completed | Done | Remaining balance updates while employee edits hours. |
| Block hours when remaining reaches zero | Completed | Done | New entries are disabled when allocation is exhausted. |
| Prevent over-planned hours server-side | Completed | Done | Backend rejects add/update/submit if hours exceed allocation. |
| Improve timesheet error messages from backend | Ongoing | 2026-09-30 | Surface exact backend validation messages instead of generic frontend errors. |
| Add automated tests for planned-hour limits | Next 2 Months | 2026-10-15 | Cover add, update, login/logout sessions, submit, and reduced corrections. |

## Approvals

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Route project timesheets to Project Manager | Completed | Done | Admin no longer automatically approves project timesheets. |
| Route Project Manager's own hours to PM-hours approver | Completed | Done | Project setup requires a separate approver. |
| Prevent users from approving their own timesheets | Completed | Done | Backend validation blocks self-approval. |
| Show employee details on approval screen | Completed | Done | Approval cards show employee, designation, project, period, hours, and status. |
| Letter approvals go to Admin/Super Admin | Completed | Done | Letter request review is Admin/Super Admin gated. |
| Validate end-to-end approval notifications | Ongoing | 2026-09-27 | Confirm PM receives notification and Admin sees only intended operational items. |
| Add approval audit/report filters | Next 2 Months | 2026-10-31 | Add filters by approver, project, status, and period. |

## Reports And PDF Export

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Restrict PDF export until approval | Completed | Done | Export is disabled unless project submission is approved. |
| Export approved project timesheet PDF | Completed | Done | PDF includes employee, project, period, daily entries, sessions, status, approver, and date. |
| Remove export-time rate override | Completed | Done | Reports no longer accept a manual bill-rate parameter. |
| Admin/Super Admin monthly reports | Completed | Done | Reports page supports monthly summary and exports. |
| Improve report rate display for multi-project months | Next 2 Months | 2026-10-20 | Consider project-level export rows when a monthly timesheet spans multiple projects. |
| Add report QA checklist | Next 2 Months | 2026-10-25 | Verify PDF/XLS values against UI totals before release. |

## Reminders And Notifications

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Add configurable timesheet reminders | Completed | Done | Defaults support 7 days before month-end and final working week reminders. |
| Stop reminders after submission | Completed | Done | Reminder tracking avoids duplicates after submission. |
| Account for weekends/non-working days | Completed | Done | Reminder logic skips weekends. |
| Admin reminder configuration UI | Ongoing | 2026-10-04 | Settings exist; improve UI labels and operational clarity. |
| Add notification delivery method options | Next 2 Months | 2026-11-01 | Define email/in-app/Teams or future notification channels. |

## UI And Theme

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Center logo on login page | Completed | Done | Login logo alignment fixed. |
| Compact oversized input fields | Completed | Done | Project and admin screens use tighter fields. |
| Concise admin navigation | Completed | Done | Profile moved under avatar; settings moved to top icon; admin menu reduced. |
| Rename project Settings tab to Project Details | Completed | Done | New project creation stays on Project Details until saved. |
| Add labels to add-employee project row | Completed | Done | Assignment row now has compact labels in one line. |
| Add colorful Chronos theme | Completed | Done | Theme now uses teal, indigo, amber, improved surfaces, and status chips. |
| Responsive visual QA pass | Ongoing | 2026-09-29 | Check project, timesheet, reports, and approvals on laptop and mobile widths. |
| Replace text-only utility controls with icons where useful | Next 2 Months | 2026-10-18 | Add icon buttons for settings, export, clock, approve/reject where appropriate. |

## Data And Migration

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Add project status and project budget migration | Completed | Done | Project status and allocated-hour fields added. |
| Add assignment bill rate migration | Completed | Done | Assignment-level bill rate added. |
| Preserve existing users/timesheets compatibility | Ongoing | 2026-10-05 | Validate old rows with missing assignment planned hours or bill rates. |
| Add migration for strict assignment required fields | Next 2 Months | 2026-10-12 | Consider backfilling missing start/end/bill-rate data before adding stricter DB constraints. |
| Add data cleanup/seed process for fresh environments | Next 2 Months | 2026-10-10 | Document clean DB setup, Super Admin seed, sample projects, assignments, and plans. |

## Testing And Release Readiness

| Task | Status | Target | Notes |
| --- | --- | --- | --- |
| Backend compile verification | Completed | Done | Recent backend changes compile successfully. |
| Frontend production build verification | Completed | Done | Recent frontend changes build successfully. |
| Add service tests for project assignment permissions | Next 2 Months | 2026-10-15 | Cover Admin, Super Admin, Project Manager, PM-hours approver, and employee restrictions. |
| Add UI regression checklist | Next 2 Months | 2026-10-22 | Include login, project creation, assignment, timesheet entry, approval, PDF export, reports. |
| Add end-to-end happy-path test | Next 2 Months | 2026-11-05 | Employee logs hours, submits, PM approves, employee exports PDF. |
| Add end-to-end rejection correction test | Next 2 Months | 2026-11-09 | Employee submits, PM rejects, employee edits, resubmits, PM approves. |
