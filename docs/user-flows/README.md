# Chronos user flows

Company-owned plans, platform-admin complimentary plans, paid employee pools, extra seats, trials and billing recovery are described in [Company billing](../company-billing.md). Its implemented policy supersedes older plan/limit wording in the diagrams below. Free now lasts sixty days, trials end by the original Free deadline, and expired access retains history and pending review workflows. Payment review is a separate screen even when online payment is unavailable. Platform company administration includes directory filters, contacts, plan/usage/expiry, company audit and delivery recovery metadata.

Seven Archify workflow diagrams based on the permission matrix. These are policy-oriented user journeys, not a claim that every target rule is already implemented. Consult the migration progress document for implementation status.

Open an HTML link below in a browser. Each standalone diagram supports themes, search, zoom, presentation and export. PA means Project Admin; company PA means company-scoped Project Admin. The platform flow includes a handoff to a different actor, the initial Company Admin.

| User flow | Diagram | Source | Delivery receipt | Browser evidence |
|---|---|---|---|---|
| Submit and handle confidential reports | [HTML](confidential-reports.html) | [JSON](confidential-reports.json) | [Receipt](confidential-reports.receipt.json) | [Evidence](confidential-reports.visual-check.html) |
| Request leave or employment letters | [HTML](leave-and-letters.html) | [JSON](leave-and-letters.json) | [Receipt](leave-and-letters.receipt.json) | [Evidence](leave-and-letters.visual-check.html) |
| Manage company people and roles | [HTML](people-administration.html) | [JSON](people-administration.json) | [Receipt](people-administration.receipt.json) | [Evidence](people-administration.visual-check.html) |
| Provision and operate the platform | [HTML](platform-administration.html) | [JSON](platform-administration.json) | [Receipt](platform-administration.receipt.json) | [Evidence](platform-administration.visual-check.html) |
| Create and administer a project | [HTML](project-creation.html) | [JSON](project-creation.json) | [Receipt](project-creation.receipt.json) | [Evidence](project-creation.visual-check.html) |
| Submit and review time or expenses | [HTML](work-approval.html) | [JSON](work-approval.json) | [Receipt](work-approval.receipt.json) | [Evidence](work-approval.visual-check.html) |
| Workspace access | [HTML](workspace-access.html) | [JSON](workspace-access.json) | [Receipt](workspace-access.receipt.json) | [Evidence](workspace-access.visual-check.html) |

## Sources and interpretation

[Implemented timesheet period flow](timesheet-periods.md): past draft editing, daily/weekly submission, batch submission, and late-entry review.

- [Permission matrix](../permission-matrix.md): role scopes, review routing and safeguards.
- [Migration progress](../migration-progress.md): implementation status.
- `frontend/src/CompanyContext.jsx` and `frontend/src/workspaceAccess.js`: company context and scoped navigation.
- `backend/src/main/java/com/maxwell/chronos/service/CompanyAccessService.java`: reviewer eligibility, Moderator grants, self-review denial and reasoned fallback.
- `backend/src/main/java/com/maxwell/chronos/service/ProjectService.java`: initial owner and project assignment.
- `backend/src/main/java/com/maxwell/chronos/service/CompanyWorkflowAccess.java`: confidential-handler and subject-specific grants.

Main-path arrows are unlabeled because adjacent actions fully express their sequential meaning. Exception arrows carry the conditions that change the path. Cards retain policy details outside the main journey. Support access and restricted-field grants are future capabilities and remain denied.

## Verification

All seven delivered diagrams passed 9/9 showcase checks with zero composition errors or warnings. Automated Chrome evidence passed at 1440x900, 1600x1000, 1920x1080 and 2048x1320, with light/dark captures at the endpoint sizes. Rendered light and dark screenshots were separately inspected for readability, routing and clipping. Viewer interactions and exported files were not separately exercised.

[Complete handoff receipts and SHA-256 hashes](handoff.json).

Timesheet and pricing regression coverage, validation results, and remaining test boundaries are documented in [timesheet/pricing validation](../../reports/timesheet-pricing-edge-validation.md). Reproduction commands are in the [E2E README](../../frontend/e2e/README.md).

Profile submission requires gender, race, ethnicity, joining date, phone number, personal email, address line 1, city, state/province, postal code, country, and the emergency contact’s name, relationship, phone, and email. Gender, race, and ethnicity use dropdowns; race and ethnicity include a “Prefer not to say” choice. Address line 2 and blood group remain optional. Submitted personal details stay locked, while previously missing details can be completed.

Company Admins can use View Profile for active members in their selected company. It displays personal identity, demographics, address, contact and emergency contact fields; SSN, blood group and credentials are excluded. Profile viewing is audited and view-only; administrators edit company employment details separately. Submitted personal details remain locked, except employees may maintain phone, address and emergency contacts. Company-only User and Project Manager invitations create membership without project roles; project assignment can happen later.

Employment details freeze after the first admin save. A correction request records its reason and requires approval by a different Company Admin (never the subject or requester). Approval permits one personal-profile save by the employee or one employment save by a Company Admin; the save completes the request and relocks the fields. Existing employment records with a prior edit are locked at migration.
