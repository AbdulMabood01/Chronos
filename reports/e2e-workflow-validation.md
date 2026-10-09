# E2E workflow validation — 2026-10-06

Both application defects from the original report are fixed. All four previously failing regressions now pass.

The suite contains 33 scenarios. All 33 have been verified passing across the earlier validation runs and the focused runs after these fixes. A single full-suite run was not repeated after the two fixes.

## 1. Timesheet company ownership — fixed

`backend/src/main/java/com/maxwell/chronos/service/TimesheetService.java`, `toDTO()`, now includes `.companyId(timesheet.getCompanyId())` in the main timesheet response.

The field comes from the persisted timesheet. The frontend can validate ownership against the selected company and render the timesheet instead of showing `Timesheet not found`. Existing authorization checks remain in effect.

Validation: all **10 timesheet Playwright scenarios passed**, including both previously failing browser regressions:

- User creates hours in the browser and the manager approves them.
- Previous-month timesheet status is visible after approval.

The suite also passed rejection/correction/resubmission, historical opening decisions, manager escalation, self-approval denial, Project Admin fallback and Moderator grant restrictions.

## 2. Expense budget query — fixed

`backend/src/main/java/com/maxwell/chronos/service/ExpenseService.java`, `budgetCheck()`, now uses `CAST(? AS BIGINT) IS NULL` for the nullable exclusion ID.

PostgreSQL can resolve the parameter type when a new expense has no exclusion ID. A null ID includes all committed expenses; a non-null ID excludes the existing expense during resubmission.

Validation:

- Both **expense Playwright scenarios passed**: browser submission/correction/resubmission/approval with totals, and project chart layouts at desktop and mobile widths.
- PostgreSQL prepared and executed the corrected query with null and non-null exclusion IDs.
- All **34 existing backend tests** in `TimesheetWorkflowTest` and `ExpenseServiceTest` passed, with no failures, errors or skips.
- Whitespace checks passed.

## Commands used

From `frontend`:

```powershell
npm run test:e2e -- timesheet-workflows.spec.cjs
npm run test:e2e -- expenses.spec.cjs project-chart-layout.spec.cjs
```

From the repository root:

```powershell
mvn -f backend/pom.xml "-Dtest=TimesheetWorkflowTest,ExpenseServiceTest" test
```

The browser runs used the real Spring E2E backend, dedicated `chronos_e2e` PostgreSQL database, Vite and Microsoft Edge. The two production-code changes were verified through **12 passing Playwright scenarios**.

## Previous validation and artifacts

Before these application fixes, 29 scenarios had passed across the full run and focused reruns. The remaining four failed because of the two defects described above. Their original screenshots, contexts and traces remain under `reports/e2e-regressions/` as historical failure evidence; they do not represent the fixed application's current status.

Current Playwright output is under `frontend/playwright-report/` and backend test results are under `backend/target/surefire-reports/`. See `frontend/e2e/README.md` for fixture isolation, focused commands and coverage details.
