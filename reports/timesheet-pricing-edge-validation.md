# Timesheet and pricing regression validation

Date: October 7, 2026. Validation uses Edge, the Spring E2E profile and PostgreSQL `chronos_e2e`.

## Current policy captured by the tests

Historical DRAFT and REJECTED periods remain editable with a valid assignment. SUBMITTED periods freeze entry changes. APPROVED periods require a Project Admin opening after the period closes and within 30 days of close; approved openings last seven days. Rejection and resubmission preserve late status and audit history. A different authorized person must review every submission, including Manager and Moderator work. The database tier matrix activates each actual Free/Pro/Pro Plus/Pro Max entitlement and confirms that Manager self approval and self rejection remain denied. Company Admin and Platform Admin roles do not grant project approval rights.

Weekly periods run Monday through Sunday and can span months/years. Batch selections must contain 1�31 distinct approval periods within one calendar month and commit atomically. Scheduled frequency changes preserve prior periods and take effect at the old frequency's next boundary. Submission deadlines use employee timezones.

Pricing is server-authoritative: Free permits one open project and seven people; paid pools permit Pro 3/75, Pro Plus 7/175, and Pro Max 15/375 projects/employees. Paid pure administrators do not consume employee seats. Prepaid 3/6/12-month terms receive 0/10/20 percent discounts; extra employee seats cost $4 per month before term discount. Quotes and unpaid redirects do not grant paid access.

## Coverage matrix

| Area | Regression assertions |
|---|---|
| Daily/weekly/monthly lifecycle | Historical entries, late submission, duplicate submission denial, rejection with reason, correction, resubmission, approval, frozen entries, repeated decision denial, exact audit sequence |
| Roles and self review | Employee, Manager, Project Admin, Moderator, Company Admin, second Company Admin, company project creator, Platform Admin, other-company admin; self approval and self rejection denied; Manager escalation and reasoned Project Admin fallback |
| Batch | Empty/null/oversized/cross-month selections, duplicate period, different dates in the same week, pending-period failure rollback, successful chosen-period submission |
| Boundaries | Weekly December/January identity, leap/non-leap month ends, cross-month hours, calendar links, four DST deadline dates, future period denials, approved reopening at 1/30/31 days |
| Frequency changes | All six source/target combinations, prior history preservation, old/new boundary split, future submission denial, scheduled-change cancellation |
| Concurrency and revocation | Same-calendar creation deduplication, duplicate submission, competing approve/reject decisions with one audit event, grant revocation affecting existing sessions |
| Browser behavior | Daily/weekly/monthly submission and manager approval, period navigation, late marks, frozen read-only display, continued work on independent periods, batch selection, historical correction and PDFs |
| Assignment | Inclusive start/end dates, dates outside assignment, inactive assignment, zero-hour assigned periods, Project Admin exclusion despite company privileges |
| Pricing quotes | All nine paid tier/term combinations with seats; exact integer-cent subtotal/discount/total; forged price/capacity fields ignored; unsupported plan/term/seat/kind rejected |
| Capacity | Concurrent final-slot creation for all four published tiers; archive releases slot and preserves archived records; paid employee pools; reservation/email deduplication; admins versus employee access |
| Financial lifecycle | Once-only trial, prepaid calendar terms, expiry at exact PostgreSQL-representable instants, exclusive seven-day grace end, restricted mode preserving records, confirmed payment fulfillment, duplicate/reordered events, unpaid/mismatched events, stale quotes, provider/local-write gap, upgrades, future renewals, seat proration, refunds and receipt reissue |
| Isolation | Billing reads/mutations denied for non-company-admin roles; other-company purchase isolation; suspension survives payment; revoked company access preserves another company |

## Validation

Added 45 Playwright scenarios and 43 backend boundary/transactional cases. Updated the old historical-draft expectations, the project fixture-count assertion, and a contradictory assignment flag in a UI fixture.

| Check | Final result |
|---|---|
| Playwright against real HTTP/Edge/PostgreSQL | 89 distinct scenarios passed across focused runs |
| Backend unit/controller regressions | 69 passed, no skips/failures/errors |
| Transactional PostgreSQL regressions | 28 passed: 9 timesheet and 19 billing tests, no skips/failures/errors |
| Frontend UI/permission regressions | 40 passed across TimesheetDetail, Billing and workspaceAccess |
| Syntax, test compilation and tracked-file whitespace checks | Passed |

The broad 71-case browser run passed 70; one new Moderator test initially tried to log hours on its seeded zero-hour assignment. The test now explicitly allocates eight hours through the owner API before logging work. The final 21-case run passed all cases, including that correction, two date-independent cases, four project/invitation regressions and all existing billing/timesheet workflows. Overlapping cases are counted once in the 89-scenario total. This was not a single full-repository E2E run.

The pricing matrix checks exact cents and ignores forged prices/capacities. Negative tests assert specific status codes and preserved persisted state; concurrency tests assert one winner and one audit event. The database tests roll back fixture records. No development or production database was used.

Final backend command:

```powershell
mvn -q -f backend/pom.xml "-Dchronos.localDbTest=true" "-Dchronos.testDbUrl=jdbc:postgresql://localhost:5432/chronos_e2e" "-Dtest=TimesheetPeriodLocalDbTest,CompanyBillingLocalDbTest" test
```

## Reproduction

See [E2E README](../frontend/e2e/README.md) for commands and database isolation requirements. Database tests and Playwright fixtures must not run concurrently against the same database.

## Limits of this validation

This is extensive regression coverage of the current policy, not proof of every possible input or race interleaving. Playwright API tests use real HTTP authorization and PostgreSQL; UI tests additionally exercise Edge. Calendar/frequency unit tests stub repository lookups. Transactional billing tests simulate verified Stripe/provider responses; live hosted Checkout, real charges/refunds, external SMTP delivery and production provider outages are not exercised. Browser capacity grants are explicit fixture contracts, not paid purchases. No production application code is changed by this test update.
