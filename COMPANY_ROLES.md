# Company and project access

Chronos uses one user account per email address. A user may belong to several companies, but each membership and role assignment is scoped to its company or project. Company membership starts only after the user accepts a unique, email-bound invitation. An invitation expires after 30 days; expired and revoked invitations no longer reserve a team slot.

## Roles

| Role | Scope | Access |
| --- | --- | --- |
| Platform Admin | Platform | Provisions companies and their initial Company Admin, manages platform administrators, sign-in access, plans, availability, and platform audit. No company operational or private-profile access. |
| Company Admin | Company | Manages company people, settings, leave, announcements, letters, scoped reports/audit, project oversight, and additional admin appointments. Sensitive workflows require separate grants. |
| Project Admin | Company and project | A company assignment permits project creation. A project assignment permits project settings, staffing, budgets, cadence, closure, and fallback approval with a reason. Creation identifies an explicit initial owner, who receives the project role. A Company Admin can appoint a different owner without receiving project administration themselves. Additional Project Admins can be appointed. |
| Project Manager | Project | Reviews team timesheets and expenses. Cannot edit project settings. May submit their own hours and expenses if actively assigned to that project. |
| Moderator | Company, with dated project grants | May approve or reject only the granted submission types during the grant period. Cannot edit project data or request expense changes. |
| User | Project | May submit hours and expenses when actively assigned. |

One person can hold several roles across projects or companies. A Project Admin cannot submit hours or expenses on a project they administer, even if they also have the User role there. They may submit as a User on a different project. No reviewer may decide their own submission.

User submissions route to a Project Manager. A Project Manager's own submissions route to a Project Admin. When a Project Manager is unavailable, a Project Admin can approve or reject a User submission by entering a reason. The reason is stored with the review history. A dated Moderator grant can authorize the specified review type.

## Tenant setup

The Platform Admin creates a company and queues its initial Company Admin invitation transactionally. Email delivery has visible status and retries; the company survives SMTP failure. Once a Company Admin is assigned, Company Admins manage later appointments. The Company Admin invites Project Admins and Moderators. A company-scoped Project Admin creates projects, appoints a Project Manager, and invites the team. Invited people can register a new account or sign in to an existing account using the invited email address. They then accept the company invitation.

The company stores a plan tier and project and team limits. Project creation and team invitations enforce those limits. Platform Admins may change tiers and limits with revisions and an audited reason; lower limits must cover current usage. Billing integration remains outside the application.

The V45 migration places existing development accounts and projects in a `legacy-chronos` company. Legacy memberships and role columns are preserved as historical compatibility data. Current access comes exclusively from scoped assignments and active membership/account/company state; startup does not promote legacy global Admin accounts.
