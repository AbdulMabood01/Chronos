# Chronos user flows

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
