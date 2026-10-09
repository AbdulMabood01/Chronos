# Scoped permission contract (Chunk 3)

The new contract computes permissions from current server-side assignments. Existing menus and legacy endpoints are migrated in later chunks; this contract does not by itself enable those features or replace their authorization.

## Platform context

`GET /api/companies/context` keeps its existing company catalog and active membership fields, and adds:

```json
{
  "platformPermissions": {
    "roles": ["PLATFORM_ADMIN"],
    "capabilities": {
      "canCreateCompanies": true,
      "canAccessCompanySupport": false
    }
  }
}
```

Ordinary members receive an empty platform role list and false platform capabilities. Platform company selection is metadata administration; it grants no company operational capabilities, even if an operational role was assigned to the same account.

## Selected company

`GET /api/companies/{companyId}/context` validates access as before. Its company metadata now contains a `permissions` field:

```json
{
  "id": 12,
  "name": "Company A",
  "permissions": {
    "companyId": 12,
    "companyRoles": ["COMPANY_ADMIN"],
    "capabilities": {
      "canManageCompanyPeople": true,
      "canManageCompanySettings": true,
      "canCreateProjects": true,
      "canReviewWork": false,
      "canHandleConfidentialReports": false
    },
    "projects": [
      {
        "projectId": 101,
        "roles": [],
        "capabilities": {
          "canViewProject": true,
          "canManageProject": false,
          "canSubmitWork": false,
          "canReviewWork": false
        }
      }
    ]
  }
}
```

Examples show a subset of returned capability keys. A company role list contains only assignments with company scope. Project-scoped roles are returned inside their specific project. Company-wide summary access does not synthesize project-admin or reviewer roles.

Active account and membership are required. Project role eligibility requires an active project membership and a matching company/project assignment. Moderator grants require the company Moderator role, matching company and project, permitted work type, an inclusive date range evaluated in UTC, and no revocation.

`canReviewWork`, `canReviewTime`, and `canReviewExpenses` describe reviewer eligibility. Actual approval still checks the submission owner, self-approval prohibition, and applicable fallback reason. Submission flags likewise describe role eligibility; the operation must check project/assignment status, dates, and workflow state. These snapshots cannot authorize a mutation on their own.

Company capabilities describe role entitlements under the migration matrix. Features whose APIs/data have not yet been migrated must remain unavailable in the UI until their implementation batch is complete. `canManageCompanySettings` enables only `/companies/{companyId}/settings`; global settings return HTTP 410. Platform configuration uses `/platform/settings` and `canConfigurePlatform`. Company leave administration now uses `/companies/{companyId}/leave`; Company Admin project creation and scoped operational reports are implemented. Company communications, letters, feedback/reviews, confidential cases, sensitive grants, and redacted audit now use company-scoped APIs. Do not substitute capability flags into legacy global endpoints.

`canManagePerformanceReviews` and `canHandleConfidentialReports` now require current dated grants in the selected company. Review operations additionally check the specific subject; confidential operations additionally check case recusal. `canManageSensitiveGrants` belongs to Company Admins and does not grant content access. Restricted personal-field and platform support access remain false. Unknown capability names are denied.

## Frontend use

`useCompany()` now exposes:

- `companyPermissions`, `companyRoles`, and `companyCapabilities` for the selected company.
- `projectPermissions` and `permissionsForProject(projectId)` for individual projects.
- `platformCapabilities` separately from company capabilities.
- `hasCompanyCapability(key)` and `hasPlatformCapability(key)`, which return true only for an explicit true value.

Use these accessors rather than the legacy flat `user.roles` list or global capability booleans. No capabilities are stored in browser persistence. Missing permission data, a pending switch, a failed context refresh, logout, or a company-ID mismatch makes the accessor deny access. Role changes are picked up by context revalidation without a new login.

Server mutations must recheck the actual resource's permissions. `CompanyAccessService.requireCompanyCapability` provides a default-deny company entitlement guard for migrated company services; resource-specific and confidentiality restrictions still apply.
