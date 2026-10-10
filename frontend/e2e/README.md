# Playwright E2E workflows

Run from `frontend`:

```powershell
npm run test:e2e
npm run test:e2e -- critical-workflows timesheet-workflows letter-workflows vacation-workflows
```

This suite uses real APIs and Edge against Vite on port 5174 and the Spring `e2e` profile on port 8081. It is separate from `npm run test:ui`, which runs browser tests with mocked APIs.

The default database is `chronos_e2e`. Fixtures reset the dedicated database before each test; the backend refuses reset/startup fixture initialization unless the database name ends in `_e2e`. Never point this suite at development or production data. Existing local servers are not reused. Fixtures wait for application readiness before browser tests start.

The fixtures include two companies, company admins, a company-scoped project creator, assigned project admin/manager/user accounts, a time-only Moderator grant, and a separate platform administrator. The employee is a member of both companies but has project roles only in the first. Monthly, weekly, and daily projects have historical assignments. Shared role definitions are preserved across resets.

Coverage:

- `critical-workflows`: explicit project owner, setup/activation, creator scope, invitation/account claim and project membership, denied invitations and platform operations.
- `timesheet-workflows`: browser hours/submission/approval, previous monthly periods and editing/opening deadlines, rejection/correction/resubmission, manager escalation, self-approval denial, Project Admin fallback, and historical daily-period opening decisions.
- `letter-workflows`: browser request/preview/approval/rejection, PDF contents, company isolation and self-approval restrictions.
- `vacation-workflows`: browser draft/submission/approval/rejection, balances, overlap/stale revisions, exhausted quotas, and clearing data on company switching.
- Existing management, employee experience, expense and account security regressions use scoped endpoints and roles.

Historical dates derive from the fixture server's date, with a January/December rollover assertion. The daily project supplies a closed period ten to twelve days ago so reopening is exercised throughout the month without changing the application clock. Vacation dates avoid weekends and federal holidays.

Tests remain sequential because reset changes shared fixtures. Login helpers reuse sessions within a test and honor `Retry-After` if the authentication limiter returns 429. The suite can pause for a minute between groups of tests. Account-lock tests still exercise the real authentication protection.

Company invitation tokens are read through a restricted E2E-only fixture endpoint; no SMTP server is required. On failure, inspect `playwright-report` and `test-results` for screenshots and traces.

## Timesheet and pricing edge-case regressions

Run the comprehensive focused browser/API suite from frontend:

```powershell
npm run test:e2e -- critical-workflows timesheet-period-matrix pricing-tier-matrix timesheet-workflows timesheet-period-submission timesheet-period-navigation timesheet-presentation project-approval-access workflow-edge-cases billing.spec
```

- `timesheet-period-matrix`: daily/weekly/monthly role matrices, self approval/rejection, owner submission denial, reasoned fallback, historical rejection/correction/resubmission, finalization immutability, comment limits, atomic batch rollback and validation, exact 30/31-day reopening, year/month boundaries, DST deadlines, queue scope, concurrent opposing decisions, browser submission/approval, and all six scheduled frequency transitions with cancellation.
- `pricing-tier-matrix`: every paid plan and prepaid term with extra seats, authoritative server calculations, invalid selections, unpaid quote/receipt isolation, company/role isolation, quote cancellation, and concurrent project limits/archive slot release for Free, Pro, Pro Plus and Pro Max.
- `workflow-edge-cases`: revoked grants and sessions, concurrent creation/submission/review, future periods, historical drafts, finalized-hour/leave conflicts, and letter/suspension regressions. Drafts remain editable regardless of age; only approved periods can request an opening within 30 days after close.

Run backend financial lifecycle tests from the repository root against the isolated database while no Playwright run is active:

```powershell
mvn -f backend/pom.xml "-Dchronos.localDbTest=true" "-Dchronos.testDbUrl=jdbc:postgresql://localhost:5432/chronos_e2e" "-Dtest=CompanyBillingLocalDbTest,TimesheetPeriodLocalDbTest" test
mvn -f backend/pom.xml "-Dtest=TimesheetPeriodBoundaryTest,PlanCatalogBoundaryTest,PlanCatalogTest,TimesheetWorkflowTest,CompanyBillingControllerTest,StripeGatewayTest" test
```

The database timesheet tests check inclusive assignment boundaries, inactive assignments, zero-hour submission, duplicate-week rollback, reviewer revocation, rejection audit, and seven-day opening expiry. The database billing tests use transaction rollback and simulated Stripe responses to cover payment fulfillment, duplicate/reordered events, upgrades, renewals, refunds, trial expiry, all tier capacities and exact grace boundaries. PostgreSQL stores timestamps at microsecond precision. Browser project-capacity tests use explicit platform contract grants; they do not represent paid purchases. Live Stripe Checkout is not exercised by this suite.

## Free expiry, payment review and platform visibility

`project-admin-approval-browser.spec.cjs` exercises Project Admin approval of employee and Manager hours across daily/weekly/monthly periods, required fallback reasons and rejection. `platform-company-overview.spec.cjs` covers platform-only metadata, directory filters, dated Free grants, public policy pages and responsive layout. `billing.spec.cjs` navigates to payment review while checkout is unavailable. The account-creation flow in `critical-workflows.spec.cjs` accepts the current Terms version.

See [validation handoff](../../reports/free-billing-platform-policy-validation.md) for results and pending configuration.
