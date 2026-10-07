# Chronos permission migration: Chunk 1

Date: 2026-10-05

Status: implementation baseline for the migration. This document specifies target behavior; it does not describe permissions already enforced by the application. No application or database changes are part of Chunk 1.

## Role definitions

| Display name | Role key | Assignment scope | Purpose |
|---|---|---|---|
| Platform super admin | `PLATFORM_ADMIN` | Platform only | Operate the platform and manage company provisioning, plans, and platform administrators. |
| Company admin | `COMPANY_ADMIN` | One company | Administer membership, company policies, communications, and company operations. |
| Company project admin | `PROJECT_ADMIN` | One company, no project | Create projects and coordinate project administration. Existing projects still require an explicit project assignment for editing and approvals. |
| Project admin | `PROJECT_ADMIN` | One project within one company | Manage the assigned project and its escalation workflow. |
| Project manager | `PROJECT_MANAGER` | One project within one company | Coordinate the assigned team and review its submitted work. |
| Moderator | `MODERATOR` | One company plus explicit project grants | Review time or expenses within the grant's dates and permitted work types. |
| User | `USER` | One project within one company | Submit personal project work and view permitted project information. |

A company membership is not a role. An active member may use personal company workflows without a project assignment, but cannot submit project work until assigned a qualifying project role.

The company-scoped and project-scoped Project Admin use the existing role key with different assignment scopes. The UI must label them distinctly. A company-scoped Project Admin receives a project assignment when creating a project; it does not automatically administer every existing project.

## Rules that apply to every permission

1. Deny any operation not expressly granted below. Menu visibility is not authorization.
2. Resolve company membership, role scope, and grants from current server state. A legacy role, selected company, cached capability, or token claim is not sufficient by itself.
3. Company operations require an active account, active company membership, and a resource belonging to that company. Project operations also require the applicable project membership and assignment.
4. Roles combine only within the requested company and project. Company A's roles never authorize Company B's resources.
5. A Company Admin who also has a project role can exercise that project role. A Project Admin cannot submit work on a project they administer, even if also assigned User or Project Manager there.
6. Platform administration is a separate mode. Platform Admin alone never grants operational company permissions. Do not combine platform administration with operational company roles during the migration; any later dual-role policy needs an explicit design.
7. Never permit self-approval. This restriction overrides other roles and grants.
8. Every view permission also governs search results, counts, attachments, exports, background jobs, and notification content. Exporting does not bypass record visibility or field privacy.
9. Company suspension blocks operational writes and normal member access; platform administrators retain company metadata and recovery controls. Suspended companies' background delivery jobs must be paused.
10. Membership removal revokes company access while retaining historical records. It does not deactivate the global login or remove membership in other companies.

## Platform and company administration

Legend: Platform = platform scope; Company = selected authorized company; Project = explicitly assigned project; Own = actor's own records; Grant = explicit additional permission; No = no permission from that role. Table columns represent roles independently, not a user's combined roles.

| Operation | Platform Admin | Company Admin | Company Project Admin | Project Admin | Project Manager | Moderator | User/member |
|---|---|---|---|---|---|---|---|
| Create a company and invite its initial admin | Platform | No | No | No | No | No | No |
| List company metadata across the platform | Platform | No | No | No | No | No | No |
| View company details | Platform metadata | Company | Company | Company member summary | Company member summary | Company member summary | Company member summary |
| Change company name and business details | No | Company | No | No | No | No | No |
| Change workspace ID | No initially | No initially | No | No | No | No | No |
| Suspend/reactivate a company | Platform | No | No | No | No | No | No |
| Set plan tier, project limits, and team limits | Platform | No | No | No | No | No | No |
| View company plan and usage summary | Platform | Company | No | No | No | No | No |
| Manage platform administrators | Platform | No | No | No | No | No | No |
| Configure SMTP, authentication, and platform integrations | Platform | No | No | No | No | No | No |
| View platform operational audit and system health | Platform | No | No | No | No | No | No |
| View company operational records without a company role | Support grant only | Not applicable | Not applicable | Not applicable | Not applicable | Not applicable | Not applicable |

Workspace IDs remain immutable in the first migration release because invitations and access requests depend on them.0000000l Renaming can be added later with a documented alias policy.

## People and role administration

| Operation | Platform Admin | Company Admin | Company Project Admin | Project Admin | Project Manager | Moderator | User/member |
|---|---|---|---|---|---|---|---|
| View company employment directory | No | Company | Company, limited fields | Assigned project, limited fields | Assigned team, limited fields | Granted team, limited fields | Minimal company directory |
| Invite another Company Admin | Initial provisioning/recovery only | Company | No | No | No | No | No |
| Invite company Project Admins and Moderators | No | Company | No | No | No | No | No |
| Invite a User or Project Manager to a project | No | Company | Own administered projects | Project | No | No | No |
| Assign/remove company-scoped roles | No | Company | No | No | No | No | No |
| Assign/remove project administrators | No | Company | No | No | No | No | No |
| Assign/remove project Users and Managers | No | Company | Own administered projects | Project | No | No | No |
| Manage employment details and company membership | No | Company | No | No | No | No | No |
| Remove a member from one project | No | Company | Own administered projects | Project | No | No | No |
| Remove membership from the company | No | Company | No | No | No | No | No |
| Issue password recovery for a company member | No | Company, deliver to verified account email | No | No | No | No | Own self-service recovery |
| Set/read another person's password or reset token | No | No | No | No | No | No | No |
| Change global login identity or suspend a global account | Platform account-support permission | No | No | No | No | No | Own verified identity change, if supported |
| View sensitive personal fields | No by default | Separate restricted grant | No | No | No | No | Own fields |

Company Admins manage company membership rather than global identity. Company admins may not reset another user's password directly or change their login email. A minimal directory contains only business identity information; it excludes leave balances, personal contact details, medical information, and confidential reports.

Protect the last active Company Admin across role removal, membership removal, suspension, and global account suspension. Serialize concurrent changes. An invitation is not an active replacement admin. Transfer project ownership and required approval responsibilities before removing their current holders.

## Projects, time, and expenses

| Operation | Platform Admin | Company Admin | Company Project Admin | Project Admin | Project Manager | Moderator | User/member |
|---|---|---|---|---|---|---|---|
| View company project summaries | No | Company | Company | Assigned project | Assigned project | Granted project | Assigned project |
| Create a project and designate its owner | No | Company | Company | No from project role alone | No | No | No |
| Edit project setup, budget, rates, or billing configuration | No | Explicit project admin assignment | Own administered projects | Project | No | No | No |
| Archive/reopen a project | No | Company lifecycle oversight | Own administered projects | Project | No | No | No |
| Appoint/transfer project owner | No | Company | No | No | No | No | No |
| Submit personal time/expenses | No | Qualifying project role required | Qualifying role on a different project | No on administered project | Own project work | Qualifying project role required | Own assigned project work |
| Review ordinary User time/expenses | No | Explicit reviewer role/grant required | Project assignment and escalation rules | Reasoned fallback | Assigned project, excluding self | Grant | No |
| Review a Project Manager's time/expenses | No | Explicit reviewer role/grant required | Explicit project admin assignment | Assigned project, excluding self | No | Grant | No |
| Grant/revoke Moderator approval access | No | Company | No | No | No | No | No |
| View time and expense totals | No | Company | Company operational summaries | Project | Assigned team | Records needed for grant | Own |
| Export time and expense records | No | Company, permitted fields | Company operational summaries | Project, permitted fields | Assigned team, permitted fields | Granted review records | Own |
| View missing submissions and team leave calendar | No | Company | Company operational summary | Project | Assigned team | Grant-relevant summary | Own status |

Company Admin oversight does not grant unrestricted project editing or approval. For those actions, assign an explicit Project Admin role or a bounded review grant. The creation flow must identify the initial project owner and create the required project role/membership together.

Normal User submissions go to the assigned Project Manager. A Project Admin can act as fallback with a recorded reason. A Manager's own submission goes to a different assigned Project Admin or an authorized Moderator. A Moderator grant specifies company, project, work type (time, expenses, or both), start date, and end date. Preserve existing period-opening rules and apply the same reviewer restrictions to opening decisions.

If no eligible reviewer exists, leave the item pending and tell the Company Admin to appoint one. Never silently bypass approval or fall back to a platform administrator.

## Company policies and employee workflows

| Operation | Platform Admin | Company Admin | Company Project Admin | Project Admin | Project Manager | Moderator | User/member |
|---|---|---|---|---|---|---|---|
| Configure company policies, calendars, and leave categories | No | Company | No | No | No | No | No |
| Set leave entitlements or individual overrides | No | Company, audited | No | No | No | No | No |
| Apply annual leave policy | No | Company only, preview and confirm | No | No | No | No | No |
| Request personal leave or letters | No | Own | Own | Own | Own | Own | Own company membership |
| Approve/reject leave and letters | No | Company, excluding self | No | No | No by default | No from time/expense grant | No |
| Configure letter templates, employer profiles, HR signatories, and branding | No | Company | No | No | No | No | No |
| View leave balances and request detail | No | Company | Own | Own | Own | Own | Own |
| View others' leave in team calendar | No | Company, minimal calendar fields | Company summary | Project summary | Team summary | Grant-relevant summary | No by default |
| Draft/publish company announcements | No | Company | No | No | No | No | No |
| Read/acknowledge company announcements | No | Company member audience | Company member audience | Company member audience | Company member audience | Company member audience | Company member audience |
| Publish platform service announcements | Platform | No | No | No | No | No | No |
| Submit employee feedback | No | Company member | Company member | Company member | Company member | Company member | Company member |
| Read employee feedback | No | Own received/sent only | Own received/sent only | Own received/sent only | Own received/sent only | Own received/sent only | Own received/sent only |
| Create/publish performance reviews | No | Explicit review permission | Explicit review permission | Explicit review permission | Explicit review permission | No by default | No |
| View performance reviews | No | Own published reviews; others require explicit review permission | Own published reviews; others require explicit reviewer access | Own published reviews; others require explicit reviewer access | Own published reviews; others require explicit reviewer access | Own published reviews | Own published reviews |
| View company audit history | No | Company, redacted sensitive content | Own administered project events | Project events | Own actions | Own actions | Own activity receipts |

A Company Admin's own leave or letter request requires a different eligible Company Admin. Calendar summaries expose dates and availability, not medical reasons, attachments, or sensitive request details. Company admins cannot edit another user's submitted feedback or inspect every private feedback item just because they administer the company. Reviewer assignment and publication status control access to performance reviews; review subjects cannot publish or approve their own review.

## Confidential reports and restricted access

These permissions are additional grants, not automatic consequences of the roles above:

| Permission | Who may hold it | Scope and restrictions |
|---|---|---|
| Handle confidential workplace reports | Explicitly designated active company members | One company; access case content, attachments, history, and decisions only when authorized for the case. |
| Manage confidential-report handler grants | Company Admin | One company; grants and revocations audited. Another Company Admin must authorize a grant to the acting admin themselves. |
| View restricted personal fields | Explicitly designated company administrators | Specific field set, legitimate business purpose, company membership, and audited access. |
| Manage company performance reviews | Company Admin or designated reviewer | Company/team/employee scope must be explicit; no self-review publication. |
| Platform support access | Platform Admin with an approved support grant | One company, purpose, expiry, permitted operations; read-only default and audited access. |

Confidential reports do not appear in generic project reports, ordinary audit exports, or platform dashboards. Report subjects do not gain case access by holding an administrative role. Anonymous reporter identity stays hidden from company reviewers; do not expose it through exports or audit events. Reporters see only the case status and information explicitly shared with them. Attachments use the same authorization as the case.

Company administrators must designate an eligible confidential-report handler during configuration. If none is configured, expose the configuration gap and retain submitted reports without routing their contents to platform admins or arbitrary managers. Cases involving a handler require recusal and another eligible handler.

Support access is a future capability. Until the grant and audit mechanism is implemented, deny platform access to operational company content. No persistent, invisible super-admin bypass. Company consent is the default for support grants; any exceptional recovery process must be documented and separately audited.

Ordinary support grants exclude confidential reports and restricted personal fields. Access to those requires separate, specifically scoped authorization; a general support grant is insufficient.

## Capabilities for the upcoming API and UI work

The names below are proposed contracts for Chunk 3. Return them for the selected company; do not derive them from a flat cross-company role list. Where an action depends on a specific project or record, include that resource's capabilities too.

| Capability | Target rule |
|---|---|
| `canManageCompanyPeople` | Active Company Admin in selected company. |
| `canAssignCompanyRoles` | Active Company Admin, within role and last-admin restrictions. |
| `canManageCompanySettings` | Active Company Admin; company settings only. |
| `canManageLeavePolicy` | Active Company Admin; selected company and year. |
| `canCreateProjects` | Active Company Admin or company-scoped Project Admin. |
| `canManageProject` | Explicit Project Admin assignment for this project. |
| `canArchiveProject` | Company Admin or explicit Project Admin for this project. |
| `canSubmitWork` | Qualifying project role; not Platform Admin or Project Admin on that project. |
| `canReviewWork` | Specific role/grant, submitter relationship, work type, dates, and no self-approval. |
| `canManageCompanyAnnouncements` | Active Company Admin. |
| `canReviewLeaveAndLetters` | Active Company Admin, excluding own requests. |
| `canViewCompanyReports` | Active Company Admin, with field and confidentiality restrictions. |
| `canViewCompanyAudit` | Active Company Admin, redacted company audit scope. |
| `canManagePerformanceReviews` | Explicit review permission and subject scope. |
| `canHandleConfidentialReports` | Explicit active confidential-handler grant; case eligibility still checked. |
| `canViewRestrictedPersonalFields` | Explicit field-access grant; target member eligibility still checked. |

Platform capabilities belong in a separate platform context: company provisioning, company suspension, plan management, platform-admin management, system configuration, platform audit, and support access. Capabilities guide UI rendering; every server operation rechecks its requirements.

## Known gaps in the current application

- Active menus, routes, and management operations use scoped roles. Replaced global management routes return HTTP 410. Legacy columns/services remain compatibility history.
- `/auth/me` now returns own identity/profile and scoped platform identity. Company/project permissions come from the selected company context.
- Company Admin/company Project Admin creation with an explicit initial owner is implemented in Chunks 8 + 9. Project editing and work review still require project assignments.
- Platform administration is limited to provisioning, plans, availability, sign-in controls, and platform audit; company/private content has no platform bypass. Initial admin onboarding stops once a Company Admin is assigned.
- Project-only administrators have assigned-project directory scope; company-wide People administration belongs to Company Admins.
- Employment, settings, leave policies/allowances/requests, and operational reports now have company scope. Communications, letters, performance/confidential grants, and redacted audit are now company-scoped; legacy endpoint retirement is complete in Chunk 12. Details are in `migration-progress.md`.
- Confidential-handler and subject-specific performance-review grants are implemented in Chunks 10 + 11. Restricted-field and platform support grants remain future capabilities and are denied.
- Bootstrap now creates only an explicit initial Platform Admin when none exists; it never promotes existing legacy `ADMIN` accounts.

## Examples that must remain true

1. Alice is Company Admin in A and User in B. Selecting B hides administrative menus, and A's role cannot authorize B's API requests.
2. Ben is a Project Admin on A1. He cannot create A2 from that assignment alone, inspect A2's roster, or submit his own work on A1.
3. Cara is company-scoped Project Admin in A. She can create A2 and receives its project-admin assignment, but cannot edit existing A1 without assignment.
4. Dana is a Company Admin. She can appoint project admins and inspect company totals; approving a project's work requires an eligible reviewer assignment/grant.
5. Evan is a Project Manager. He approves his team's User submissions, while his own work requires a different authorized reviewer.
6. Fran has an expenses-only Moderator grant for A1 through a given date. She cannot approve timesheets, access A2, or approve after expiry.
7. Removing Grace from A immediately blocks A access while preserving her active membership in B and her historical A records.
8. A Platform Admin can provision A and see its plan, but cannot download a confidential report or inspect payroll content without the appropriate explicit access mechanism.
9. Two Company Admins trying to remove each other concurrently cannot leave the company without an active admin.
10. A company admin's policy change affects only that company's leave balances; their own leave request cannot be self-approved.

## Chunk 1 completion and handoff

Chunk 1 provides the role model, operation matrix, scope rules, escalation rules, confidentiality restrictions, capability vocabulary, and examples for later tests. Implementation is intentionally deferred.

Recommended defaults adopted in this baseline: Company Admin may create projects but appoints an explicit owner; operational approval remains delegated; confidential and restricted personal data require additional grants; platform support is read-only by default and explicitly authorized.

Next: Chunk 2 implements company selection and validated company context. Chunk 3 implements scoped capabilities. Chunk 4 changes menus only where the matching API already enforces these boundaries. Each later chunk implements and tests its own matrix rows.
