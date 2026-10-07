# Company plans and billing implementation plan

Date: October 6, 2026

Status: planning document only. No application code, configuration, migration, database, Stripe account, or deployment changes are authorized by this document. Prices remain launch proposals until approved for implementation.

## 1. Objective and decision history

Introduce company-owned Free, Pro, Pro Plus and Pro Max plans, optional extra-user capacity, prepaid purchases, and Stripe payments while preserving current company/project authorization and the universal never-self-approve policy.

The latest conversation supersedes earlier pricing proposals:

- The company owns the plan. It does not belong to a Project Admin account.
- Free includes one project and seven people.
- Paid project allowances are three, seven and fifteen.
- The latest accepted planning input proposes company-wide paid user pools of 75, 175 and 375, replacing the earlier assumption of 25 users per paid project.
- Paid company reference prices are $149, $299 and $599 per month equivalent.
- Additional paid capacity is proposed at $4 per user/month equivalent.
- Purchases cover 3, 6 or 12 months; proposed discounts are 0%, 10% and 20% respectively.
- Preserve one-off payment purchases and explicit renewal; no automatic renewal or silent charge.
- Independent approvals, ordinary roles, exports and audit integrity remain part of the product, not premium authorization exceptions.

The pooled allowances, prices, term discounts and detailed lifecycle policies are implementation-planning baselines. The open-decision register below identifies clarifications needed before they become production rules. Earlier research is evidence, not an additional conflicting specification.

## 2. Plan catalog and prices

All amounts are USD before applicable tax. Monthly figures are reference rates, not a new month-to-month product. Display the actual prepaid amount prominently.

| Plan | Open projects | Included company users | Monthly reference | Extra users |
|---|---:|---:|---:|---|
| Free | 1 | 7 | $0 | Upgrade required |
| Pro | 3 | 75 | $149 | $4/user/month equivalent |
| Pro Plus | 7 | 175 | $299 | $4/user/month equivalent |
| Pro Max | 15 | 375 | $599 | $4/user/month equivalent |
| Custom arrangement | Negotiated | Negotiated | Written quote | Written quote |

Custom is an optional contracted exception path, not a replacement for the three named paid tiers. Ordinary extra-user purchases remain self-service within a published technical capacity; unusually large deployments or bespoke services use a quote. The self-service ceiling is still to be decided and should reflect validated capacity, not an invented sales threshold.

| Plan | 3-month purchase | 6-month purchase, 10% off | 12-month purchase, 20% off |
|---|---:|---:|---:|
| Pro | $447.00 | $804.60 | $1,430.40 |
| Pro Plus | $897.00 | $1,614.60 | $2,870.40 |
| Pro Max | $1,797.00 | $3,234.60 | $5,750.40 |

| Extra seat | 3 months | 6 months | 12 months |
|---|---:|---:|---:|
| Monthly reference after term discount | $4.00 | $3.60 | $3.20 |
| Full-term price per extra seat | $12.00 | $21.60 | $38.40 |

Use a server-owned, versioned catalog. Store money in integer cents; use explicit decimal rounding for prorated quotes. Clients provide plan/term/quantity selections, never authoritative prices, limits, discounts or payment status. Keep each purchase's price and entitlement snapshot even when the catalog changes later.

Public plan keys proposed: `FREE`, `PRO`, `PRO_PLUS`, `PRO_MAX`, with contracted overrides separately recorded. Keep display labels independent of keys. Do not simply rename old database values and assume the limits are equivalent.

## 3. Included functionality and deferred features

The normal application workflow remains complete in every plan, within its capacity. A 30-day paid-feature trial is proposed; it cannot be restarted by changing administrators or creating a new purchase attempt for the same company.

| Capability | Free | All paid tiers | Implementation treatment |
|---|---|---|---|
| Project time entry, timesheets and current submission cadences | Included within capacity | Included | Preserve existing behavior |
| Expenses, receipt attachments and review | Included within capacity | Included | Expense attachments are distinct from subscription billing receipts |
| Independent review, rejection/resubmission and reasoned fallback | Included | Included | Never self-approve; no billing bypass |
| Multiple Project Managers and scoped roles | Included within capacity | Included | Preserve role scope and separation of duties |
| PTO policies, balances, requests and current approval flow | Included within capacity | Included | Preserve company privacy and subject restrictions |
| Timesheet reminders and ordinary notifications | Included | Included | Respect company status and background-job scope |
| Standard reports and PDF/Excel/data exports | Included | Included | Enforce existing record visibility on exports |
| Invitations, profiles, company people administration | Included | Included | Tie activation to capacity; preserve account lifecycle safeguards |
| Standard audit history and account security | Included | Included | Retain history through changes; no paid security exception |
| More projects and workforce capacity | 1 / 7 | Catalog allowances plus extra seats | Primary upgrade drivers |
| Public supported API, standard SSO, maintained connectors | Not a launch promise | Proposed future included capability once ready | Separate delivery/readiness work; internal REST/OAuth dependencies are not sufficient |
| Custom report builder, forecasting, deeper integrations | Packaging undecided | Roadmap subject to decision | Existing reports remain included; do not invent Business as a fourth named plan |
| Priority support, SCIM, contractual SLA, bespoke rollout | Contract scope | Contract scope | Sell only after staffing/capabilities are validated |

The earlier research's short project-start retention windows are not adopted. Do not hide historical records or delete audit data to force an upgrade. A formal storage, cancellation-access and retention policy is still needed; no automatic purge belongs in the initial billing release.

## 4. Counting projects, people and purchased capacity

### Projects

Recommended rule: all projects except `COMPLETED` and `ARCHIVED` consume an open-project slot, including `DRAFT`, `PLANNED` and `ON_HOLD` where those statuses exist. Keep one definition in usage displays, creation, reopening, import and plan-change checks. Completing/archiving a project releases a slot and retains its data. Reopening consumes a slot and must recheck capacity.

### Company users

Paid allowances are distinct workforce users per company, not project membership rows. One account on several projects or in several roles counts once in that company. The same person enrolled with another company is counted independently by that company.

Proposed workforce-user definition: an active company member enrolled to use personal time, expenses, PTO or other employee workflows. A role label alone cannot make an employee free. An administrator who also uses employee workflows consumes one user slot. Pure administration/view-only accounts on paid plans are free and cannot gain employee workflow access without workforce enrollment and a capacity check.

Free clarification proposed for review: seven active human company members including its Company Admin/Project Admin, so the advertised seven-person team remains a real limit. Free accounts are free of charge, but administrators consume the seven-person allowance. This resolves an ambiguity between the earlier seven-person team wording and the later free-admin-only recommendation; confirm it before coding.

Removed memberships, expired invitations and revoked invitations do not consume active-user slots. Global temporary sign-in locks should not automatically free a licensed workforce slot: otherwise security actions could accidentally change billing or permit oversubscription. Explicit company offboarding releases the slot after existing ownership/review-duty safeguards pass. Historical records are retained under existing access rules.

### Purchased extra seats versus active users

Separate these quantities on every billing/usage screen:

1. Included user allowance.
2. Purchased extra seats.
3. Active workforce users.
4. Pending reservations for additional distinct workforce people.
5. Available capacity and proposed reductions at the next term.

Capacity = included allowance + paid, fulfilled extra seats + any active trial/contract override.

Billing is for explicitly purchased capacity for the current prepaid term. It is not a retrospective charge on activity. Vacant purchased seats can be reassigned and are not automatically refunded. This clarifies the earlier phrase '$4 per active user': only extra capacity intentionally purchased is charged; active-user count determines whether that capacity is needed.

Invitations never automatically create a charge. A valid pending invitation may reserve existing capacity, but it cannot grant unpaid capacity. When no capacity remains, show an authorized billing administrator a purchase/upgrade option before sending another capacity-consuming invitation. Sending a second role invite to an existing member consumes no additional slot. Deduplicate pending reservations by normalized email/account across projects and roles. Free reservations include the applicable administrator invitations as well.

Recheck capacity transactionally at acceptance, workforce enrollment, role changes that enable employee work, membership reactivation, imports and project reopening. A prior reservation is not permission to bypass current entitlement or company availability. Expiry, revocation and acceptance release/convert reservations without double-counting.

Company-wide pooling removes the paid 25-person per-project cap. All licensed company users can participate in a project subject to normal access rules. Product diagrams and help text must explicitly reflect that change during implementation.

## 5. Extra-user purchases and quotes

Examples at undiscounted monthly reference rates:

| Configuration | Formula | Monthly equivalent |
|---|---|---:|
| Pro, 80 workforce users | $149 + 5 × $4 | $169 |
| Pro, 100 workforce users | $149 + 25 × $4 | $249 |
| Pro Plus, 180 workforce users | $299 + 5 × $4 | $319 |
| Pro Max, 400 workforce users | $599 + 25 × $4 | $699 |

Prorated seat purchase proposal:

`charge cents = round(extra quantity × full-term seat price cents × remaining term seconds / total term seconds)`

The discount comes from the active term's purchased catalog version. New extra seats end at the base term's existing end; buying seats does not renew the plan. Use UTC instants, calendar-month term addition and one rounding rule. Show remaining days, charge, tax, capacity and unchanged end date before confirmation. Define whether the quote fixes the charge for its brief validity or is regenerated before Checkout; it must never become more expensive than consented without another confirmation.

Illustrative arithmetic fixture: ten extra seats on a 12-month term with exactly half its duration left cost `10 × $38.40 × 0.5 = $192.00`, before tax. Calendar halves are duration-based, not an assumption that every month has 30 days.

Quote state must include company, authorized actor, action type, current billing revision, catalog version, plan, term, quantity, monetary breakdown, old/new capacity, start/end calculation and expiration. Stripe Checkout's expiration should be aligned with the valid quote. Concurrent billing actions need serialization or mutually exclusive open intents; do not allow two successful payment attempts to silently overwrite one term.

If a stale quote is paid after company state changes, record the money and reconcile it explicitly through fulfilled entitlement, a compatible transition or refund/support resolution. Never discard a successful payment because a local revision is stale.

Display cheaper upgrade suggestions using the same server quote engine. For example, Pro plus 38 seats is $301/month equivalent, while Pro Plus includes 175 users for $299. Suggestions must consider selected term, actual immediate charge and carryover; do not automatically upgrade, remove seats or charge.

## 6. Purchase and subscription lifecycle

### Trial

- Proposed: once-per-company 30-day trial of Pro Plus capacity, enough for a realistic approval cycle; no payment card required.
- Record start/end, source and any reasoned extension. Do not treat a trial as a paid receipt.
- At trial end, apply Free if usage fits; otherwise preserve membership/data and enter the explicitly defined over-capacity state. Send advance notices and provide full authorized export.
- Exact trial capacity is an open commercial decision; it must not default to unlimited without a published operational limit.

### New purchase

Company billing administrator selects tier, term and extra seats → backend quote → review actual prepaid total → hosted Stripe Checkout → processing page → confirmed paid fulfillment → company entitlement update, ledger entry and billing receipt → updated usage.

Payment cancellation/failure leaves the prior entitlement unchanged. The browser success redirect alone never activates a plan. Return customers can revisit processing status; closing the browser must not stop fulfillment.

### Expiry and renewal

- An application subscription represents a purchased fixed term, even though Stripe is collecting a one-off payment.
- No automatic renewal or charge. Expiry notices proposed at 30, 7 and 1 days, with a renewal link for authorized company administrators.
- Buying the same plan early schedules the new term from the old end date. Do not shorten the already-paid term or overlap charges unnecessarily.
- At term expiry, extra-seat grants end with the base grant. Free becomes effective when no other paid/trial/contract grant applies.
- Entitlement checks derive validity from timestamps during requests; scheduled jobs cannot be the only expiry enforcement. Jobs update status and send deduplicated notices.
- Payment failure for a prospective renewal does not prematurely revoke the currently valid term.

### Mid-term upgrade

Retain the earlier requested duration carryover: purchase a new higher tier/term, preserve the old term's remaining duration, and mark the old term superseded only after payment fulfillment. New end = new calendar-month term end + old remaining duration. Store the calculation and linked old term for audit.

Recommend preserving the quote's agreed upgrade date/end after brief payment delay. Define a maximum pending duration and re-quote/reconciliation behavior before implementation. State clearly that remaining time carries forward; it is not a monetary refund or automatic cash proration of the base price.

Recalculate extra seats against the new included pool and show the result. Seats absorbed by the larger allowance should not be charged again. Do not blindly copy the old paid seat quantity into the new tier. Treatment of outstanding reserved capacity and long-delayed upgrades remains in the decision register.

### Reductions and downgrades

- Removing a workforce member frees reusable capacity; it does not erase records or refund the committed term.
- Schedule purchased-seat reductions and plan downgrades for renewal/end of term. Show projected capacity and required cleanup.
- Never reduce capacity below active users/reservations through a current-period manual change without an explicit supported transition.
- The future lower plan can be scheduled, but activation must handle still-over-limit usage through the published restricted state. Never automatically deactivate people, delete projects or choose whose access to remove.
- Replacing the last Company Admin or an owner/required reviewer still follows existing transfer safeguards.

### Over-capacity, expiry and operational suspension

Keep financial entitlement separate from `companies.is_suspended`. Billing expiry must not grant Platform Admin operational access, revoke global login, or silently suspend other companies belonging to the same person.

Recommended policy for review: seven-day grace after paid/trial expiry, visibly labeled. During grace, existing work can continue while new projects/new workforce enrollments above Free are blocked. After grace, an over-limit company keeps authorized reads/exports, billing recovery, offboarding and reviews of already submitted work, but new employee submissions are restricted until renewed or brought within capacity. Free companies already within one project/seven members continue ordinary Free operations. This is a proposed product behavior, not an implemented rule.

Document permitted actions explicitly for timesheets, expenses, PTO, letters, project changes, notifications and background jobs. Existing confidentiality and reviewer permissions continue to apply in every access mode. Restoration after successful payment must be immediate and idempotent.

## 7. Stripe integration, receipts and refunds

Use hosted Checkout for one-off payments (`payment` mode), with company-associated payment identity and a server-controlled catalog. Do not introduce Stripe recurring subscriptions or automatic off-session charges in the initial release. Stripe supports hosted one-time Checkout purchases. [Official Checkout guide](https://docs.stripe.com/payments/checkout/how-checkout-works?payment-ui=stripe-hosted).

Authenticate user-facing purchase actions with company billing permissions. The webhook endpoint instead verifies Stripe's signature on the unchanged raw payload, checks the bound internal purchase and confirmed payment, persists a durable event and processes it idempotently. Duplicate/reordered/retried events must not create duplicate terms, receipt records or capacity. Acknowledge only after durable acceptance; use retries/reconciliation for interrupted fulfillment. [Stripe webhook guidance](https://docs.stripe.com/webhooks), [fulfillment guidance](https://docs.stripe.com/checkout/fulfillment?payment-ui=stripe-hosted).

Verify account/environment, currency, amount, payment identifiers and the stored purchase/company mapping; browser fields and arbitrary metadata do not grant company authority. For asynchronous methods, wait for actual payment success rather than treating Checkout completion as sufficient. Persist paid-but-unfulfilled status and surface recovery. A reconciliation task resolves missed events using provider records without recharging.

### Billing receipts

- Create a company billing receipt automatically for successful plan and extra-seat purchases; retries return the same receipt identity.
- Include company legal/billing details, purchase identifier, tier/term or add-on, quantity, price version, net/discount/tax/total, currency, payment date, service dates and relevant Stripe transaction reference.
- Offer secure PDF download and delivery to authorized billing contacts. Email failure must not undo payment or entitlement.
- Reissue without creating a new charge; retain original and reissue history. Changes to billing details should not silently rewrite paid historical documents.
- These are subscription-purchase receipts, distinct from employee expense uploads and payroll reports. Do not label a receipt a tax invoice unless the supported invoice requirements are explicitly implemented.

### Seven-day refund policy

Retain the reference's seven-day eligibility as a proposed rule: eligibility begins at the verified original payment timestamp, not a reissued receipt. Check the boundary server-side for every requester. Store request reason, authorization, provider refund reference and asynchronous result. Refunds use the original payment; original documents and audit are retained. [Stripe refunds](https://docs.stripe.com/refunds).

Record `REFUND_PENDING` until provider confirmation; do not report a refund as complete simply because a request was sent. Make repeated requests idempotent. A failed refund leaves the payment history and appropriate entitlement intact with visible recovery.

Product decision: define plan/seat entitlement changes after refunds, partial refunds, carryover upgrades and chargebacks. Proposed full upgrade reversal restores the previous still-valid term at its original end, or Free if expired; resulting over-capacity uses the same safe transition policy. Do not delete acquired operational data. Extra-seat refund must resolve current usage before capacity reduction. Chargeback policy is not settled by the seven-day voluntary refund window.

### Credentials: when they are needed

Catalog, domain model, mock payment tests and initial UI can be built without a key. A Stripe test secret key and separate webhook signing secret are needed in the Stripe test-integration phase. Hosted Checkout can use a backend-provided redirect URL; a publishable key is needed only if the chosen browser integration requires Stripe.js. Live credentials are needed only for the approved production setup.

Configure secrets through local environment/secret storage at that stage; never put them in this document, source control, browser code, receipts or logs. Pin a supported stable SDK/API version during implementation; do not adopt a documentation preview version accidentally. Keep test/live provider identifiers isolated.

## 8. Billing authorization and administration

The current permission matrix permits Platform Admin plan changes and Company Admin usage viewing. Self-service company purchases therefore require an explicit policy/API extension during implementation; project budget/rate administration is not subscription billing authority.

| Actor | Proposed billing authority | Continuing restrictions |
|---|---|---|
| Company Admin with billing permission | View usage/prices/receipts; buy/renew/add seats; schedule reductions; request refunds; manage billing contact | Own company only; no direct forged entitlement update |
| Project Admin / Project Manager | View actionable project capacity messages and direct authorized administrator to resolve | No purchase/refund authority from project role alone |
| Moderator | None by default | Receipt reissue only through an explicit approved billing grant, if introduced |
| User | Own access/capacity messages | No billing contacts, company charges or receipts |
| Platform Admin | Catalog/metadata/contract override and explicit authorized finance/support actions | No timesheet, expense, payroll or private-profile bypass |
| Stripe fulfillment/reconciliation service | Apply verified purchases to their stored companies | No general user impersonation or arbitrary plan mutation |

Proposed capabilities include `canViewCompanyBilling`, `canPurchaseCompanyPlan`, `canManageCompanySeats`, `canViewBillingReceipts` and separately authorized refund/reissue actions. Decide whether all Company Admins receive them or a designated billing grant is required. Check current account/company membership and permission on every request; hide billing data in unauthorized searches, exports and notifications too.

For suspended companies, recovery billing access needs an explicit limited policy while operational suspension stays effective. Successful payment must not automatically remove an administrative suspension.

Manual grants/overrides require actor, reason, revision, duration/expiry, limits and source. Keep commercial payment records separate from support overrides. Old direct tier/limit writes must route through the new entitlement authority; no free-form limits silently override paid catalog rules.

## 9. Current app evidence and required changes

| Current evidence | Gap | Planned change |
|---|---|---|
| `V45__companies_and_scoped_roles.sql` stores FREE/SINGLE/MULTIPLE/ENTERPRISE/CUSTOM, defaults 3 projects/6 team members | New names/defaults and pooled users absent | Additive migration, explicit legacy mapping and effective entitlements |
| `PlatformAdministrationService.Plan` accepts tier plus arbitrary limits | Catalog does not determine standard allowances | Catalog-bound standard plans; separate audited contracted overrides |
| `ProjectService.saveProject` counts all company projects | Historical projects can block creation while usage reports exclude them | Shared open-project counting, including reopening/import |
| `CompanyManagementService.requireTeamCapacity` checks USER invites against per-project membership/pending invitations | Different roles/acceptance paths can bypass a future pooled cap | Company-level distinct-user reservations and activation checks |
| `CompanyMembershipService` handles reactivation and ownership/reviewer safeguards | New workforce capacity must be checked there too | Preserve safeguards and add shared transactional entitlement checks |
| `CompanyAccessService.mayReview` rejects reviewer = submitter | Must remain independent of pricing | Retain rule; regression tests for every plan/trial/override state |
| Platform plan/usage UI exists; Company Admin has company context | No company Checkout, quote, receipt or term UI | New scoped billing screens and narrow capabilities |
| No Stripe/subscription/Checkout implementation found in reviewed source | Payment and lifecycle absent | Ledger, fulfillment, receipts, reconciliation and scheduled lifecycle |
| Existing E2E report describes 33 verified scenarios across runs | Billing adds many cross-workflow risks | New isolated billing tests plus applicable workflow regressions |

Sources: [company role model](../COMPANY_ROLES.md), [permission matrix](permission-matrix.md), [migration progress](migration-progress.md), [user-flow index](user-flows/README.md), [existing workflow validation](../reports/e2e-workflow-validation.md), [pricing research](../reports/chronos-pricing-market-research-2026-10-06.md).

## 10. Proposed domain records and boundaries

Names are design proposals, not migrations or an instruction to use these exact table names.

| Record | Responsibility |
|---|---|
| Plan catalog/version and term prices | Stable keys, project/user allowances, term discounts, currency and sales availability |
| Company billing profile | Company payment identity, billing contacts, billing details and revision |
| Company purchased term | Paid tier, start/end, status, source purchase, price/entitlement snapshot, predecessor/carryover |
| Quote/purchase intent | Authorized company/action, selected scope, monetary calculation, revisions, expiration and fulfillment state |
| Extra-seat grant/change | Quantity, amount, start/end aligned with term, provider transaction, future reduction |
| Trial/contract override | Effective dates, capacity, source, approver/reason, without pretending a payment occurred |
| Payment/refund ledger | Durable financial history, provider IDs, net/tax/total, settlement/reversal states |
| Billing receipt/reissue | Document snapshot, numbering, delivery, securely scoped download, refund linkage |
| Webhook inbox/reconciliation attempts | Unique provider event and fulfillment identity, durable processing/retry history |
| Capacity reservations | Distinct prospective workforce identities, invitation relationships, expiry/release |
| Entitlement projection/cache | Derived effective limits/state from terms and grants; not another independently editable source |
| Billing activity/outbox | Audit plus reliable receipt/expiry delivery without exposing operational/private data |

Use constraints and company-scoped locking to protect one effective base term, unique fulfillment/refund references and deduplicated reservations. A future paid renewal may coexist with the current term but cannot become effective early. Do not hold database locks across a network payment request: persist intent, call provider safely with idempotency, then record result. Reconcile the provider/local gap.

Suggested service boundaries: catalog/pricing quotes; company entitlement and usage; company billing actions; Stripe gateway; verified payment fulfillment; receipt/refund service; lifecycle/reminder/reconciliation jobs. Authorization remains in existing company access services, extended with explicit billing capabilities.

Suggested company-scoped API surface (all under existing `/api` prefix): catalog read; `/companies/{id}/billing` summary; quote/Checkout purchase; purchase status; receipt list/download; extra-seat quote/purchase; scheduled reduction/downgrade; renewal; refund request. Provider webhook is a separately verified endpoint. Final routes/DTOs should follow existing conventions at implementation time.

## 11. Customer and operator journeys

| Journey | Visible behavior | Failure/edge behavior |
|---|---|---|
| Company onboarding | Platform provisions company and Company Admin under current model; Free/trial state clearly shown | Paid purchase does not create broader roles; self-service company creation is separate scope |
| Choose plan | Cards show projects, included company users, per-term total, discounts and extra-seat formula | Do not present a monthly reference as a month-to-month offer |
| Purchase/renew | Authorized admin reviews quote, pays hosted Checkout, sees processing then active status/receipt | Cancellation keeps previous plan; delayed payment remains processing until settled |
| Add users | People screen shows available/reserved slots; authorized admin can buy capacity or upgrade | No automatic charge from an invite; same account on another project consumes no new slot |
| Add extra seats | Show actual prorated charge, updated company capacity, unchanged end date | Failed/canceled payment does not expand capacity |
| Cheaper upgrade | Show current configuration versus alternative and exact charge/end dates | Suggest only; never automatically switch or collect money |
| Reduce users | Offboard through existing safeguards; reuse paid slot or schedule next-term reduction | Do not bypass last-admin, owner transfer or pending-review protection |
| Reach project limit | Explain current open count and upgrade requirement | Archived history remains accessible; reopening checks a slot |
| Expiry | Notices, term end, Free/grace/over-capacity status and recovery CTA | No deletion, global account suspension or changed reviewer authority |
| Receipt/refund | Download scoped PDF; eligible user requests refund and sees pending/completed result | Reissue preserves eligibility date; failed refund is visible, not falsely completed |
| Platform support | Inspect billing metadata/event recovery; grant reasoned dated exception when authorized | No operator access to company operational/private records through billing tools |

During implementation, add a billing flow document to `docs/user-flows/` and update people/project/platform diagrams only after their policies are settled. No existing flow diagram is changed by this planning turn.

## 12. Implementation phases and completion gates

| Phase | Deliverables | Exit gate | Stripe credentials |
|---|---|---|---|
| 0 — Resolve policy and establish baseline | Confirm decision register; inventory legacy usage; record current test baseline; approve specification | No contradictory seat, expiry, upgrade or refund rules | None |
| 1 — Catalog and deterministic pricing | Versioned tier/term catalog, cents-based calculations, effective-date/proration rules, quote contract | Exact price fixtures and term boundaries pass; no client price authority | None |
| 2 — Company capacity and migration design | Shared project/user counting, reservations, pooled capacity, all activation paths, audited override model | Concurrency and bypass cases pass against PostgreSQL; migration rehearsal maps every company | None |
| 3 — Billing domain and scoped APIs | Intent/term/payment/receipt records, status model, billing capabilities, fake gateway, company UI skeleton | Cross-company denials and mock purchase flows pass | None |
| 4 — Stripe test integration | Hosted one-off Checkout, signed durable webhook inbox, idempotent fulfillment, base receipts, reconciliation | Real test Checkout and duplicate/delayed/reordered events correctly settle once | Test secret + webhook signing secret |
| 5 — Complete lifecycle | Seats/proration, carryover upgrades, early renewal, future reductions, trial/expiry/grace, refund/reversal policies, delivery retries | All financial and entitlement transitions preserve correct company access/data | Test credentials |
| 6 — Customer/operator UI and docs | Pricing/term calculator, billing dashboard, usage/reservations, receipt/refund screens, notifications, recovery tools, user flows | Desktop/mobile journeys are reviewable and accessible; no unreleased feature promises | Test credentials |
| 7 — End-to-end and migration validation | Full scoped billing suite, applicable work-flow regressions, failure-injection/reconciliation checks, migration/recovery rehearsal | Release evidence records exact runs and limits; no unresolved payment/permission defects | Test credentials |
| 8 — Reviewed production launch | Approved prices/policies, secrets/webhook configuration, first customer pilot, monitored reconciliation and support runbook | User approves concrete release/configuration; controlled live validation and rollback ready | Live credentials only here |

No calendar estimates are asserted before policy review and implementation sizing. Each phase should produce a focused reviewable change; do not bundle identity integrations or a new report builder into the billing migration. Implementation, live writes, Stripe product creation and publishing require a later request.

### Migration and deployment approach

Use the next unused Flyway migration at implementation time. Do not edit already-applied V45. Inventory current per-company project/member usage, administrator-only users and arbitrary overrides before mapping legacy tiers. Never map SINGLE/MULTIPLE/ENTERPRISE to PRO/PRO_PLUS/PRO_MAX blindly or invent paid purchase history.

Existing development/demo companies can be assigned documented seed/trial arrangements under explicit fixture rules. Real existing companies require a reviewed legacy/contract grant that preserves current access during migration. Unknown mappings stay in a review queue; no bulk commercial downgrade or automatic billing. Migration and rollback must preserve ledger/entitlement data, even if checkout is disabled. A feature flag can stop new purchases, while webhook handling and reconciliation continue for money already accepted.

Update UI/API tier validators, public errors and fixtures together. Legacy clients should receive a clear catalog/contract response rather than a contradictory tier string. Retire direct edits to standard plan limits after compatibility migration. Changing the new entitlement projection must never silently override a manual company suspension.

## 13. Validation coverage

Use unit tests for arithmetic/state rules, real PostgreSQL tests for constraints/locking, API authorization tests, Stripe test-mode integration, and focused Playwright flows. New billing tests should exercise meaningful money/access transitions, not simply mirror getter implementations.

| Area | Required cases |
|---|---|
| Catalog/math | All nine base purchases, full-term add-ons, prorated cents/rounding, invalid term/quantity, catalog version changes, taxes separated, month-end/leap-date boundaries |
| Capacity | Free seventh/eighth person; 75/76, 175/176, 375/376 boundaries; one user across projects; free admins versus enrolled employees; same email across role invites; invitation expiry/revocation; acceptance races |
| All write paths | Invite/accept/reactivate/enroll/import/role change; PM/PA/Moderator paths; project creation/reopening; two simultaneous additions cannot exceed capacity |
| Permissions | Other-company ID/receipt forgery; project role cannot buy/refund; removed/locked admin; service company mapping; platform billing scope grants no operational/private bypass |
| Payment | Correct Checkout; browser-success forgery; amount/currency/account mismatch; failed/canceled/asynchronous payment; duplicate events and separate payment/Checkout notifications; reorder, retry and interrupted transaction |
| Concurrency/recovery | Quote stale after plan change; two Checkout attempts paid; payment succeeds before local write; durable inbox worker crash; receipt/email failure; missed webhook reconciliation |
| Terms | New purchase, early renewal, exact expiry, one trial only, trial extension audit, grace, over-limit Free fallback, renewal failure leaves active term intact |
| Seats/upgrades | Mid-term seat prorations; unchanged end; replacement user; scheduled reduction; carryover days; larger plan absorbs prior extras; cheaper-plan suggestion; no automatic upgrade/charge |
| Refunds | Seven-day boundary; reissue does not reset date; duplicate request; provider failure/pending/completion; partial/extra-seat refund; upgraded plan reversal; refund exceeds current lower capacity |
| Retained workflow | Self-approval blocked on every plan/trial/override; existing submissions still reviewed per policy; historical exports stay scoped; suspension stays in force after payment |
| UX | Correct selected company, actual prepaid totals, current/reserved/purchased seat distinction, accessible desktop/mobile controls, processing/retry states and renewal notices |
| Migration | Legacy values/overrides, historical projects, existing over-limit companies, no invented payments, rehearsal and recovery without lost records |

The existing report's 33-scenario result is prior evidence, not a claim that it covers billing or a promise every scenario has just been rerun. Collect fresh release evidence after code changes with explicit run names, environment, counts and failures.

## 14. Decisions to settle before implementation

These do not block creating this plan. They block only the dependent implementation/launch behavior.

| ID | Decision | Recommended baseline |
|---|---|---|
| D1 | Final commercial catalog | Confirm $149/$299/$599, 3/6/12 terms, 0/10/20% discounts and $4 extra-seat reference |
| D2 | Free seven-person scope | Seven active company people including administrators; paid pure-admin accounts remain free |
| D3 | Paid pooling and workforce classification | 75/175/375 distinct company workforce users, no paid per-project team cap; classify employee-workflow enrollment server-side |
| D4 | Purchased capacity versus usage charging | Explicit prepaid seats; no invite-triggered charge; vacancies reusable, reductions at renewal; show all quantities |
| D5 | Trial capacity and once-only eligibility | Thirty days of Pro Plus capacity per company; reasoned extensions only |
| D6 | Expiry and over-capacity operations | Seven-day grace and clearly scoped restricted mode; no deletion or automatic offboarding |
| D7 | Upgrade carryover and absorbed extras | Preserve remaining duration with new full purchase; rebase extras to new pool; final delayed-payment/refund treatment required |
| D8 | Billing/refund/support permissions | Company Admin billing capability; separate grant for refund/reissue/support; no Moderator default financial access |
| D9 | Refunds, chargebacks and recovery | Seven-day voluntary eligibility; specify full/partial/add-on/upgrade reversal and dispute entitlement policy |
| D10 | Tax, invoice and commercial account setup | Confirm supported jurisdictions, company billing identity, tax collection and receipt/invoice scope before live sales |
| D11 | Future feature and service packaging | Keep core workflow complete; decide advanced reports/SSO/API/SCIM/support only when ready; no fourth Business tier automatically introduced |
| D12 | Extra-seat self-service ceiling/storage/retention | Publish operational limits after capacity validation; preserve historical data; no short project-start retention gate |
| D13 | Legacy mapping and paid expiry dates | Inventory actual companies, approve compatibility grants and calendar/UTC/proration conventions; no guessed payment history |
| D14 | Price changes and notification timing | Immutable purchased prices; published renewal notice schedule and current versus next-term totals; no auto-renewal |

## 15. Definition of done

- A company can select an approved tier/term, explicitly pay once, receive the correct entitlement and secure billing receipt, and recover if a webhook/email is delayed.
- Extra-user purchases are explicit and prorated; one person is counted once across projects; project growth and user growth have clear independent rules.
- Renewal, carryover upgrades, future reductions, trial expiry, refunds and disputes have documented and tested transitions.
- No new capacity arrives from a browser redirect, forged price, duplicated provider event, alternate role path or race condition.
- No pricing state permits self-approval or broadens company/private access; historical data remains protected and recoverable.
- Company billing, people/project messages, platform tools, marketing and user-flow documentation use the same approved catalog and counting definitions.
- Migration, applicable regression checks, payment reconciliation and operational support are evidenced before production approval.
- No credentials are requested until the relevant integration phase, and no commercial capability is advertised as finished before it is delivered.

This planning turn delivers only this document. All code, migrations, Stripe configuration, tests and deployment work remain future work.
