# Free access, billing navigation, platform visibility and policies

Implemented October 9, 2026. Changes are local; no production deployment or live Stripe setup was performed.

## Delivered behavior

- V66 starts existing company Free clocks at migration rollout; new companies receive sixty days from creation. The original deadline is persistent. Expiry blocks new work even below capacity while retaining authorized history, exports, billing and pending decisions. Existing members may still receive reviewer assignments without occupying new seats.
- A once-only Pro Plus trial ends at the earlier of thirty days or the original Free deadline. No trial grace extends that deadline. Paid expiry retains its existing seven-day grace.
- Complimentary Free grants require an explicit future expiry and audited reason. Existing Free grants are bounded at rollout. Other complimentary tiers retain their existing optional expiry behavior.
- Billing quotes navigate to a dedicated payment review screen even when Stripe is unavailable. That screen displays price, dates, refund conditions and a return path. Enabled checkout requires the current Terms version; verified provider confirmation remains the only purchase activation authority.
- Platform company directory includes search, onboarding/suspension/expiry/billing/delivery filters, current usage, admin contacts, company audit history and billing-delivery retry metadata. Employee content remains outside platform authority.
- Public legal pages cover privacy, Terms, refunds/cancellation, acceptable use, retention/deletion, business DPA, browser storage and support. Copyright identifies Maxwell IT Solutions. Signup and checkout record versioned acceptance; V67 adds account acceptance fields without inventing acceptance for existing accounts.

## Verification

- Production frontend build passed. Existing bundle-size advisory remains.
- Initial broad frontend run: 257 passed; two company provisioning mock failures were corrected. Focused billing/company/permission run subsequently passed 17 tests; onboarding/purchase run passed 12 tests (three purchase tests overlap). This was not a final full-suite rerun.
- Backend PostgreSQL billing: 24 passed, zero failures/errors/skips. Boundary checks include exact expiry, trial deadline cap, expired trial denial, preserving memberships and same-person reviewer eligibility, and versioned scoped checkout acceptance. Both migrations validated/applied to chronos_e2e only.
- Backend onboarding, invitation and controller regressions: 32 passed, zero failures/errors/skips (17 onboarding, 5 invitation, 3 billing controller, 7 platform controller).
- Playwright against real HTTP/PostgreSQL/Edge: 13 passed across billing, platform overview and Project Admin review browser files. Includes six approval scenarios across three frequencies and two submitter roles, plus fallback rejection. Existing self-review/isolation API matrices are retained.
- Final account-creation Playwright journey passed with the required Terms checkbox and real account claim/sign-in, bringing focused browser coverage to 14 passing scenarios.
- Platform desktop/mobile screenshots visually inspected; the browser checks found no mobile horizontal overflow.
- Tracked-file whitespace check passed.

## Remaining deployment inputs

Set VITE_SUPPORT_EMAIL in the frontend build environment when the address is available. Stripe remains configuration-dependent; live hosted payment and real refunds were not exercised. Company deletion remains a verified support-managed process, not automatic expiry deletion. Complete the DPA deployment schedule (customer identities/signatories, subprocessors, locations, security measures and deletion/backup procedures) before executing it as a customer contract. No provider lists, certifications or legal deletion timelines were fabricated.

Privacy/security drafting references: [FTC retention guidance](https://www.ftc.gov/business-guidance/resources/protecting-personal-information-guide-business), [Illinois breach guidance](https://illinoisattorneygeneral.gov/Consumer-Protection/For-Businesses/Data-Breach/). Deployment-specific obligations require assessment against actual data processing and customers.
