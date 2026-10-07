# Playwright E2E workflows

Run from `frontend`:

```powershell
npm run test:e2e
npm run test:e2e -- critical-workflows timesheet-workflows letter-workflows vacation-workflows
```

This suite uses real APIs and Edge against Vite on port 5174 and the Spring `e2e` profile on port 8081. It is separate from `npm run test:ui`, which runs browser tests with mocked APIs.

The default database is `chronos_e2e`. Fixtures reset the dedicated database before each test; the backend refuses reset/startup fixture initialization unless the database name ends in `_e2e`. Never point this suite at development or production data. Existing local servers are not reused. Fixtures wait for application readiness before browser tests start.

The fixtures include two companies, company admins, a company-scoped project creator, assigned project admin/manager/user accounts, a time-only Moderator grant, and a separate platform administrator. The employee is a member of both companies but has project roles only in the first. Monthly and daily projects have historical assignments. Shared role definitions are preserved across resets.

Coverage:

- `critical-workflows`: explicit project owner, setup/activation, creator scope, invitation/account claim and project membership, denied invitations and platform operations.
- `timesheet-workflows`: browser hours/submission/approval, previous monthly periods and editing/opening deadlines, rejection/correction/resubmission, manager escalation, self-approval denial, Project Admin fallback, and historical daily-period opening decisions.
- `letter-workflows`: browser request/preview/approval/rejection, PDF contents, company isolation and self-approval restrictions.
- `vacation-workflows`: browser draft/submission/approval/rejection, balances, overlap/stale revisions, exhausted quotas, and clearing data on company switching.
- Existing management, employee experience, expense and account security regressions use scoped endpoints and roles.

Historical dates derive from the fixture server's date, with a January/December rollover assertion. The daily project supplies a closed period ten to twelve days ago so reopening is exercised throughout the month without changing the application clock. Vacation dates avoid weekends and federal holidays.

Tests remain sequential because reset changes shared fixtures. Login helpers reuse sessions within a test and honor `Retry-After` if the authentication limiter returns 429. The suite can pause for a minute between groups of tests. Account-lock tests still exercise the real authentication protection.

Company invitation tokens are read through a restricted E2E-only fixture endpoint; no SMTP server is required. On failure, inspect `playwright-report` and `test-results` for screenshots and traces.
