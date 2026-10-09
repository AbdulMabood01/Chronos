# Platform administration and complimentary plans

Implemented October 8, 2026.

Companies, Platform Accounts, Platform Audit and Platform Settings now share workspace titles, spacing, cards, controls and responsive layouts. Platform Admins can assign a catalog plan or Custom allowance without payment, with no expiry or an explicit local date/time. Company Billing shows complimentary status and suppresses purchase and trial prompts. Grants and revocations retain history and record platform audit events.

Validation:
- Production Vite build passed. Existing large-bundle advisory remains.
- 78 targeted backend tests passed with local PostgreSQL and V65 applied. Coverage includes no-expiry grants, dated expiry, revocation, Free capacity counting, authorization, revision conflicts, catalog limits, active/scheduled paid protection, open checkout/refund/review protection, stale quote invalidation, suspension preservation, and existing billing lifecycle behavior.
- 17 targeted frontend tests passed for Companies, Billing and Settings, including complimentary Billing without checkout or trial prompts.
- All 12 platform browser tests passed. The two layout tests were repeated after styling fixes with settled dark button color assertions.
- All four screens captured at 1440 and 390 pixels in light and dark themes; no horizontal overflow. Representative desktop and mobile screenshots were visually inspected for control styling, readability and layout. Browser APIs were mocked; real Stripe transactions were not performed.

Screenshots:

| Screen | Desktop | Desktop dark | Mobile | Mobile dark |
|---|---|---|---|---|
| Companies | [Light](companies-1440.png) | [Dark](companies-1440-dark.png) | [Light](companies-390.png) | [Dark](companies-390-dark.png) |
| Platform Accounts | [Light](platform-accounts-1440.png) | [Dark](platform-accounts-1440-dark.png) | [Light](platform-accounts-390.png) | [Dark](platform-accounts-390-dark.png) |
| Platform Audit | [Light](platform-audit-1440.png) | [Dark](platform-audit-1440-dark.png) | [Light](platform-audit-390.png) | [Dark](platform-audit-390-dark.png) |
| Platform Settings | [Light](platform-settings-1440.png) | [Dark](platform-settings-1440-dark.png) | [Light](platform-settings-390.png) | [Dark](platform-settings-390-dark.png) |

Reproduce from frontend:

```powershell
node node_modules/vite/bin/vite.js build
node node_modules/vitest/vitest.mjs run src/pages/Billing.test.jsx src/pages/Companies.test.jsx src/pages/Settings.test.jsx --minWorkers=2 --maxWorkers=2
node node_modules/@playwright/test/cli.js test browser-tests/platform-completion.pw.cjs
```

Reproduce from backend with local development PostgreSQL:

```powershell
mvn "-Dchronos.localDbTest=true" "-Dtest=PlatformCompletionLocalDbTest,CompanyBillingLocalDbTest,PlatformAdministrationControllerTest,CompanyBillingControllerTest,PlanCatalogTest,PlanCatalogBoundaryTest" test
```

Restart an already-running backend to load the new API and service code. Flyway applies V65 on startup when needed. No company grant was assigned to existing user data, and no production deployment was performed.
