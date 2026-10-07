# Pricing and plans: Maxwell reference vs. Chronos

Reviewed October 6, 2026. Reference: `itsolutions-maxwell/maxwell-time-and-expense`, branch `main`, commit `04c187a699ec54bc2bed688051a61dd7e58d1222`. Chronos assessment uses the current working tree, including uncommitted implementation. Static code/document review; no billing behavior was exercised and no application code was changed.

## Reference requirements

The reference is a design plus running skeleton, not a completed billing implementation ([README](https://github.com/itsolutions-maxwell/maxwell-time-and-expense/blob/04c187a699ec54bc2bed688051a61dd7e58d1222/README.md)).

| Plan | Owned projects | Members per team, including Project Admin | Price |
|---|---:|---:|---|
| Free | 3 | 6 | Free |
| Single | 1 | 25 | Undecided |
| Multiple Projects | 5 | 25 | Undecided |
| Enterprise | 15 | 25 | Undecided; discounted per-team rate |
| Custom | Negotiated | 26+ or bespoke needs | Contact Us |

Source: [PLAN_TIERS.md](https://github.com/itsolutions-maxwell/maxwell-time-and-expense/blob/04c187a699ec54bc2bed688051a61dd7e58d1222/docs/PLAN_TIERS.md).

- Plans belong to the Project Admin account/email and cover its owned projects, not a company or an individual project.
- Paid terms are 3, 6, or 12 months. Tier and duration are independent choices: nine standard tier/term combinations. No exact amounts, discount percentage, or per-seat pricing formula is specified.
- 16+ projects or any team with 26+ members routes to Contact Us rather than self-service checkout.
- Purchases use one-off Stripe Checkout payments. Successful webhooks create the app subscription and billing receipt and activate the plan.
- No automatic renewal or charge. Expiry returns the account to Free; existing records remain, with creation/invitations constrained by the fallback limits.
- Mid-term upgrades add remaining days to the newly purchased term and cancel the previous subscription row.
- Billing receipt PDFs are automatic; the purchaser can download their own, and Admin/Moderator can generate or reissue them within their authority.
- Refunds are allowed within seven days and enforced by the backend, with payment refunds through Stripe.
- History windows are anchored to project start: six months Free, twelve months paid. A downgrade recalculates the window from the original start date.
- Separate User project-membership caps are proposed but unresolved: Free 3–5, paid 10–12.

Sources: [PAYMENTS.md](https://github.com/itsolutions-maxwell/maxwell-time-and-expense/blob/04c187a699ec54bc2bed688051a61dd7e58d1222/docs/PAYMENTS.md), [DATA_MODEL.md](https://github.com/itsolutions-maxwell/maxwell-time-and-expense/blob/04c187a699ec54bc2bed688051a61dd7e58d1222/docs/DATA_MODEL.md), [WORKFLOWS.md](https://github.com/itsolutions-maxwell/maxwell-time-and-expense/blob/04c187a699ec54bc2bed688051a61dd7e58d1222/docs/WORKFLOWS.md).

## Comparison with current Chronos

| Area | Chronos implementation | Assessment |
|---|---|---|
| Tier names | FREE, SINGLE, MULTIPLE, ENTERPRISE, CUSTOM | Names match |
| Plan owner | `companies.plan_tier`, `project_limit`, `team_limit` | Different model: shared company pool, not owner account |
| Free defaults | Three projects, six members per project | Matches numeric defaults |
| Paid tier caps | Platform Admin manually supplies any positive limit up to 100,000 with any tier | No canonical Single 1/25, Multiple 5/25, Enterprise 15/25 mapping |
| Project creation | Server-side company lock and cap check | Present, but company-scoped |
| Team invitations | USER invitations reserve slots against active project memberships plus pending USER invitations | Partial match; other project-role invitations do not invoke capacity check |
| Plan management | Platform Admin form; revision check; required reason; platform audit event | Manual provisioning is implemented |
| Price catalog and pricing page | No commercial price/term catalog or purchase flow found | Missing |
| Terms and subscription lifecycle | No subscription entity, paid dates, expiry fallback, renewal purchase, or carryover found | Missing |
| Stripe checkout/webhooks | No integration found | Missing |
| Billing receipts and refunds | No billing receipt/refund implementation found | Missing; expense attachment receipts are a separate feature |
| Contact Us routing | CUSTOM is a manually selected tier; no commercial threshold routing found | Missing |
| History retention | No tier-derived project history window/enforcement found | Missing |
| User membership caps | No Free/paid multi-project membership cap found in reviewed onboarding paths | Missing; exact reference caps need a business decision |
| Approval behavior | Never self-approve, irrespective of plan | Deliberate policy conflict with reference Free-tier self-approval |

Local evidence:

- `backend/src/main/resources/db/migration/V45__companies_and_scoped_roles.sql:5`: company plan fields and defaults.
- `backend/src/main/java/com/maxwell/chronos/service/PlatformAdministrationService.java:21`: tier validation and independently supplied caps; lines 30–31: usage and audited plan update.
- `frontend/src/pages/PlatformAdministration.jsx:15`: editable tier and limit fields; selecting a tier does not assign a canonical limit pair.
- `frontend/src/pages/Companies.jsx:318`: company plan display; line 328: project usage.
- `backend/src/main/java/com/maxwell/chronos/service/ProjectService.java:105`: creation capacity enforcement.
- `backend/src/main/java/com/maxwell/chronos/service/CompanyManagementService.java:206`: USER-only invite capacity check; line 491: active and pending slot counting.
- `backend/src/main/java/com/maxwell/chronos/service/CompanyAccessService.java:291`: reviewer cannot equal submitter.
- `docs/permission-matrix.md:31`: no self-approval policy; `COMPANY_ROLES.md:24` explicitly places billing integration outside the application.

## Concrete inconsistencies and unresolved decisions

1. **Project usage is inconsistent.** Creation counts all company projects, while platform usage and downgrade validation exclude COMPLETED/ARCHIVED. A company can appear below its limit in administration while new project creation is blocked by historical projects. Choose one definition and apply it across UI, creation, reactivation, and plan changes.
2. **Team capacity is not applied to every role invitation.** USER invites check capacity, but Project Admin/Project Manager invites can create active memberships without that check. The reference counts the Project Admin in the team cap. Define whether all participating roles consume seats and whether extra-role invitations for an existing member should reserve another slot.
3. **Tier labels do not guarantee tier entitlements.** A FREE company can be assigned high caps, or ENTERPRISE low caps. Decide whether manual overrides are intended; otherwise centralize canonical caps and keep negotiated overrides in CUSTOM.
4. **Free to Single reduces project capacity from three to one.** This is specified by the reference, not a transcription error. It needs an explicit purchase/over-cap policy rather than treating all paid purchases as capacity increases.
5. **Reference text contains old alternatives and contradictions.** Its earlier term paragraph mentions possible auto-renew, but later dedicated sections and PAYMENTS/WORKFLOWS explicitly rule it out. DATA_MODEL says a Project Admin has no own time entries in their administered project, while WORKFLOWS describes same-project dual-role Free self-approval. Resolve that before importing plan-dependent approvals. Chronos currently has an explicit universal no-self-approval rule.
6. **Other business details remain unspecified.** Dollar prices, Enterprise discount, exact membership caps, chargeback entitlement policy, retention grace/action details, and refund effects on previously active plans are not fully defined. Do not invent these as if they were established requirements.

## Suggested implementation order

First choose the billing owner: retain company subscriptions for Chronos's current company model, or adopt account ownership with a larger entitlement migration. Then agree on prices and fixed tier caps, consistent project/seat counting, and over-cap behavior. Implement server-owned tier/term pricing, one-off Checkout and verified idempotent payment processing, subscription/receipt records, explicit renewal and expiry, upgrade carryover, and seven-day refunds. Add the pricing/upgrade/Contact Us UI, receipts, and expiry notices. Treat retention and plan-dependent approvals as separate policy decisions rather than changing the current permission matrix implicitly.
