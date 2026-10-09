# Company plans and billing

Implemented locally October 7, 2026. The company owns its plan; administrators do not own separate subscriptions. See the [implementation plan](company-plans-billing-implementation-plan.md) for design history.

| Plan | Open projects | Included people / employees | Monthly reference | 3 months | 6 months | 12 months |
|---|---:|---:|---:|---:|---:|---:|
| Free | 1 | 7 people, including admins | $0 | — | — | — |
| Pro | 3 | 75 employees | $149 | $447 | $804.60 | $1,430.40 |
| Pro Plus | 7 | 175 employees | $299 | $897 | $1,614.60 | $2,870.40 |
| Pro Max | 15 | 375 employees | $599 | $1,797 | $3,234.60 | $5,750.40 |

USD before tax. Monthly amounts are reference rates: purchases are one-off prepaid terms, never monthly recurring subscriptions. Six-month terms save 10%; twelve-month terms save 20%. Catalog: `2026-10-v1`.

## Capacity and authorization

Paid extra seats cost $4/month equivalent with the term discount: $12 for three months, $21.60 for six months, $38.40 for twelve months. Mid-term additions prorate by remaining seconds, round half up to cents, and end with the existing term.

An employee counts once per company across all projects. Paid per-project caps are removed. Administration-only accounts are free on paid plans but require explicit employee access before submitting time, expenses, PTO or letters. Global temporary login locks do not free company seats. Free counts all active people, including admins.

Pending invitations reserve capacity for distinct prospective identities. Acceptance/revocation/expiry releases or converts reservations. A second role invite consumes no additional slot. Invitation/resend/acceptance, enrollment, reactivation, project assignment and reopening share company-scoped checks. Invitations never charge. Vacant paid seats remain reusable. Completed/archived projects release slots and retain data; other statuses count.

Every available Company Admin receives explicit billing capabilities for their own company. Project roles, Moderator grants and ordinary membership do not grant billing access. Standard workflows, independent approvals, exports and audit history remain included in every tier. Nobody may approve their own work.

Administrative suspension remains effective after payment. Suspended Company Admins have a billing history/refund view; operations, new checkout and trials stay blocked.

## Purchases, expiry and refunds

Each company may start one card-free 30-day Pro Plus trial: 7 projects / 175 employees. Eligibility persists across administrators.

Checkout is explicit and hosted by Stripe. A success URL never grants access. Verified matching payment creates a term, immutable PDF receipt, audit history and queued delivery to current Company Admins. Reissue preserves the original document/payment date.

Same/lower-tier early renewal starts at the current end. A higher-tier purchase starts at the agreed quote time and adds remaining old-term duration to the new calendar-month term. Extra seats are explicitly selected against the new included pool. Quotes must cover current usage. Only one future paid renewal is allowed.

Next-renewal preferences may reduce a plan or seat allowance without changing the current paid term. They are advisory for the next manual purchase and never charge automatically. No renewal means Free at expiry.

Existing work receives seven days of grace after paid/trial expiry; new capacity immediately follows Free. After grace, over-limit companies cannot submit new time, expenses, leave or letters. Authorized history, exports, pending reviews, billing recovery and offboarding remain available. No automatic deletion, offboarding or global suspension occurs. Entitlements derive from timestamps during requests; jobs are not the expiry authority.

Full refunds may be requested within seven days of original verified payment. Pending refunds retain access until confirmation. Confirmed base refunds reverse their grant and can restore a still-valid paid predecessor. Extra-seat refunds reverse only their seats and require sufficient remaining capacity. Base purchases with dependent changes require coordinated support review. Receipts remain available.

Seats awaiting a refund are reserved against new invitations/enrollments, preventing a pending refund from selling the same capacity again. Open checkout and pending refunds serialize conflicting purchases. The current purchased allowance remains visible until refund confirmation.

Partial/additional external refunds and disputes enter a visible review state; access remains pending a finance decision. Partial refunds, coordinated multi-purchase reversals and dispute resolution are not automatic self-service actions. No automatic chargeback suspension is applied.

## Migration and recovery

V64 preserves existing companies with explicit `LEGACY` grants covering configured limits and current usage. It fabricates no payment history and does not rename old tiers. New companies start at Free 1/7. Legacy grants need business review before commercial conversion.

Platform Admins see payment/event metadata and may retry a bound verified event with an audited reason. Billing grants do not expose operational/private records. Manual standard grants use catalog limits; Custom permits explicit exceptions. Existing support/contract grants last 90 days. Platform Admins can also assign complimentary Free, Pro, Pro Plus or Pro Max plans (or a Custom allowance) from Companies, with no expiry by default or an explicit end date. Complimentary grants use the normal plan features and capacity checks, require no card or checkout, and create no purchases, invoices or payment receipts. Only Platform Admins can assign, change or revoke them; a reason and current company revision are required. Platform Audit records the actor and reason; company billing activity records the grant event without exposing private platform notes.

An active complimentary plan appears in Company Billing with its expiry or **No expiry**, and purchasing and trials are blocked until the grant is revoked or expires. Ask a Platform Admin to change its capacity. Active or scheduled paid terms cannot be replaced. Open checkout, pending refunds and payment review must be resolved before a grant changes. Revocation returns the company to Free and requires usage to fit 1 open project / 7 people, including admins and reserved invitations. Dated expiry returns to Free immediately through timestamp-based entitlements; over-limit work becomes restricted while history, exports and pending reviews remain available. No paid/trial expiry grace applies to complimentary grants. No data is deleted.

Money is integer cents. Quotes/receipts retain catalog and billing snapshots. Fulfillment validates mode, environment, currency, amount and stored company/purchase identity. Raw-body webhook HMAC verification, a durable inbox and payment idempotency prevent duplicate grants. Provider/local session-write gaps are recoverable. Stale paid quotes remain recorded for review/refund. Missed payment/refund confirmations reconcile through Stripe without recharging.

Receipt/reminder delivery retries without undoing payment and rechecks current billing permission before sending. Renewal notices at 30/7/1 days and expiry are deduplicated. Reminders never charge.

## Configuration and review

Public pricing: `/pricing`. Company Admin billing: `/billing`. Public catalog: `/api/billing/catalog`. Company APIs: `/api/companies/{id}/billing`. Webhook: `POST /api/billing/stripe/webhook`.

Checkout defaults to disabled. Configure through local environment or deployment secret storage, never source control or chat:

```text
BILLING_CHECKOUT_ENABLED=true
BILLING_WEB_ORIGIN=http://localhost:5173
STRIPE_SECRET_KEY=<test-mode secret key>
STRIPE_WEBHOOK_SECRET=<test webhook signing secret>
STRIPE_LIVE_ENABLED=false
STRIPE_AUTOMATIC_TAX=false
```

Forward test Stripe events to `http://localhost:8080/api/billing/stripe/webhook`. Webhook and API secrets are distinct. The direct API integration pins `2025-02-24.acacia`. Hosted Checkout needs no browser publishable key here. New-checkout shutdown does not stop configured webhooks/refunds/reconciliation for accepted payments.

Tax collection can use Stripe automatic tax; receipts are not tax invoices. Before live sales, confirm seller identity, tax/jurisdiction support, finance procedures, capacity/storage limits and the commercial catalog. Live keys need explicit opt-in and a reviewed rollout. No live setup or production deployment was performed.

Mock provider responses and real PostgreSQL validate price/capacity/lifecycle/recovery without keys. Actual Stripe calls, hosted Checkout and real signed delivery still need a configured sandbox run. Future SSO/SCIM, supported public APIs, custom reports and support SLAs are not advertised as completed capabilities.
