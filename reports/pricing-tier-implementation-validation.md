# Pricing tier implementation validation

Date: October 7, 2026 (America/Chicago).

## Delivered

Company-owned Free / Pro / Pro Plus / Pro Max catalog, 1/3/7/15 open projects, Free 7 people, paid employee pools 75/175/375, explicit discounted/prorated extra seats, 3/6/12-month prepaid terms, once-only 30-day Pro Plus trial, seven-day expiry grace and safe restricted mode.

Company billing permissions, profile snapshots, quote/Checkout status, immutable PDF receipts/reissue, seven-day full refunds, pending-refund seat reservations, future renewal preferences, duration-carryover upgrades and preserved company suspension. Hosted Stripe integration, raw-body signature verification, durable event inbox, duplicate/out-of-order protection, reconciliation and provider/local-write gap recovery are implemented.

Public pricing, Company Admin billing, employee-access controls, desktop/mobile layouts and Platform Admin payment-metadata/event-retry tools are present. Workflow/private access and never-self-approval remain independent of billing.

See [billing guide](../docs/company-billing.md) for exact prices, implemented policy, configuration and the limits of self-service refund/support behavior.

## Validation results

| Check | Result |
|---|---|
| Full backend suite, local PostgreSQL enabled | 416 discovered; 402 passed, 14 existing skips; zero failures/errors |
| Final focused backend run after configuration/reminder adjustments | 36 passed across catalog, Stripe signatures, real-DB billing, HTTP authorization and platform lifecycle tests |
| Frontend regression suite | 258 passed in 39 files |
| Frontend production build | Passed; existing large-bundle warning remains |
| Browser suite in isolated `chronos_e2e` | 7 passed: 3 billing journeys, 3 management navigation journeys, 1 project approval-access journey |
| Migration | V64 applied and validated against local PostgreSQL; E2E database also migrated |
| Desktop/mobile visual review | Screenshots inspected; 390px browser assertion verified no horizontal overflow |
| Source whitespace check | Passed |
| Local startup | Backend started, actuator health UP, pricing page HTTP 200, catalog returns all four correct tiers |

Commands:

```text
backend: mvn -q -Dchronos.localDbTest=true test
backend: mvn -q -Dchronos.localDbTest=true -Dtest=PlanCatalogTest,StripeGatewayTest,CompanyBillingLocalDbTest,CompanyBillingControllerTest,PlatformCompletionLocalDbTest test
frontend: npm test -- --reporter=dot
frontend: npm run build
frontend: npm run test:e2e -- billing.spec.cjs project-approval-access.spec.cjs management-navigation.spec.cjs
```

Focused billing coverage includes all nine base prices, term discounts, calendar/leap boundaries, cent rounding, every paid-pool boundary, Free admins/reservation deduplication, real concurrent seventh/eighth-person invitations, unchanged seat end dates, upgrade carryover, scheduled renewal, raw-byte signature tampering/environment checks, browser-success forgery, unpaid/mismatched events, duplicate fulfillment, stale paid quotes, local session-write gap recovery, cross-company denial, refund confirmation/window/reissue, seat-refund dependencies, pending-refund enrollment races, partial external refund review, cancellation, suspension preservation and platform recovery scope.

Existing regression fixtures were updated to match the current compact employee-ID and scoped moderator policies, plus the new membership insert and explicit test-company capacity grants. The corresponding app policies were retained. Isolated E2E grants are explicitly marked as fixture contracts, not paid purchases.

Local `chronos_dev` inspection after validation: one company, five users, one preserved active legacy grant, zero billing purchases, zero remaining `billing-test-*` companies. Billing service tests roll back their fixture data. Existing companies retain legacy access pending commercial conversion; no payment history was invented.

## Review artifacts

- [Desktop billing screen](billing-desktop.png)
- [Mobile billing screen](billing-mobile.png)
- Ignored raw logs: `billing-backend-tests.log`, `billing-focused-tests.log`, `billing-frontend-tests.log`, `billing-frontend-build.log`, `billing-browser-tests.log`.

The local review app runs at `http://localhost:5173`: public `/pricing`, Company Admin `/billing`. Checkout/live mode and external email delivery are disabled in that local review process. Backend uses the existing local signing key and local development database.

## Stripe sandbox verification pending credentials

Local automated payment tests use mock provider responses with real PostgreSQL. They do not prove a real Stripe Checkout charge, Stripe-hosted browser flow or actual provider webhook delivery. No live payment or production deployment was performed.

The user requested a real test-mode checkout. As of this validation, `STRIPE_SECRET_KEY` and `STRIPE_WEBHOOK_SECRET` were not found in root `.env`, the Windows process/user/machine environment, or the checked local configuration files. Credential values were never printed. The local credential file/location has been requested; live keys will not be used for this verification. This sandbox check remains pending until the test credentials are accessible.

Production finance/tax/legal rollout, stress-tested operational ceilings, partial refunds, coordinated multi-purchase reversal and dispute resolution still require their documented review/configuration. Future SSO/SCIM/custom reporting are not part of this pricing release.
