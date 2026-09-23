# Project Health

`GET /projects/health` derives current health without storing manual health fields or changing operational data. Admins and Super Admins see all projects. Project reviewers see only projects allowed by the existing project visibility rules. Other employees receive 403. Other-project assignments contribute to aggregate capacity counts without exposing their names, IDs, or budgets.

The response includes Healthy / Attention Needed / At Risk status, structured reasons, lifetime hours through the server's current date, remaining allocation, planned resource hours, active resource counts, submission and approval counts, and coverage notes. The most severe reason determines status. Results and reasons are ordered by severity.

Rules:

- Use the existing project hour budget when present; otherwise derive it from assigned resource hours. Missing budget produces an attention signal; zero is a real budget, not missing data.
- Hour usage of 80% needs attention; 95% or an overrun is at risk. Remaining hours may be negative. Logged hours include draft, rejected, submitted and approved entries, once each; submission snapshots are not added again.
- Logged hours exceeding resource plans are at risk. Resource plans exceeding the project hour budget need attention.
- Assignment dates provide a delivery-window proxy, explicitly labeled as an assignment window rather than a contractual project deadline. Within 14 days needs attention, within 7 days or past the end is at risk.
- At least 50% hour usage and a lead of 20 percentage points over elapsed assignment timeline needs attention; at least 80% usage with that lead is at risk.
- Active projects with no active employees assigned today are at risk of understaffing, except when their assignment timeline has not started.
- Potential overallocation means a resource's overlapping active-project assignments exceed eight hours per weekday. Hours are spread evenly over each assignment's full window. Only present/future overlaps involving the evaluated project count. Leave, holidays, and individual work schedules are not modeled.
- Missing submissions count distinct employee/project/month combinations in completed months. Expected months come from active dated assignments with planned hours and at least one weekday, plus historical logged entries or nonzero draft/rejected submissions. Assignment creation dates prevent retroactive expectations before onboarding. Submitted, change-requested, approved and locked submissions satisfy submission requirements; current-month drafts are not overdue.
- Submitted and change-requested project submissions await approval. Rejected submissions require correction. Ended assignments retain alerts for actual historical records, but do not generate expectations for new submissions.
- Delivery checks are paused for draft, on-hold, completed and archived projects; recorded outstanding timesheets still generate alerts.

The current schema has no expense records, expense budgets, monetary project budgets, milestones, or explicit project start/deadline fields. These checks are disclosed as unavailable rather than inferred from bill rates or fabricated. No schema migration is needed. Capacity and assignment-window checks are estimates, not delivery guarantees.

The UI refreshes on page load, project mutations, approval changes, window focus, and manual overview refresh. Project health uses current data independently of the selected reporting month. Failed requests show an explicit unavailable/retry state and never a healthy result.

Validation: `mvn -Dtest=ProjectHealthServiceTest,ProjectPermissionsTest test`; frontend health, project-management and admin-dashboard Vitest suites; `npm run build`; `npx playwright test project-health.pw.cjs`.
