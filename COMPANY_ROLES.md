# Company and project access

Chronos uses one user account per email address. A user may belong to several companies, but each membership and role assignment is scoped to its company or project. Company membership starts only after the user accepts a unique, email-bound invitation. An invitation expires after 30 days; expired and revoked invitations no longer reserve a team slot.

## Roles

| Role | Scope | Access |
| --- | --- | --- |
| Platform Admin | Platform | Onboards companies and Company Admins. Cannot submit hours or expenses. |
| Company Admin | Company | Appoints Project Admins and Moderators, manages invitations and moderator grants, and transfers project ownership. |
| Project Admin | Company and project | A company assignment permits project creation. A project assignment permits project settings, staffing, budgets, cadence, closure, and fallback approval with a reason. The project creator is its first owner. Additional Project Admins can be appointed. |
| Project Manager | Project | Reviews team timesheets and expenses. Cannot edit project settings. May submit their own hours and expenses if actively assigned to that project. |
| Moderator | Company, with dated project grants | May approve or reject only the granted submission types during the grant period. Cannot edit project data or request expense changes. |
| User | Project | May submit hours and expenses when actively assigned. |

One person can hold several roles across projects or companies. A Project Admin cannot submit hours or expenses on a project they administer, even if they also have the User role there. They may submit as a User on a different project. No reviewer may decide their own submission.

User submissions route to a Project Manager. A Project Manager's own submissions route to a Project Admin. When a Project Manager is unavailable, a Project Admin can approve or reject a User submission by entering a reason. The reason is stored with the review history. A dated Moderator grant can authorize the specified review type.

## Tenant setup

The Platform Admin creates a company and invites its Company Admin. The Company Admin invites Project Admins and Moderators. A company-scoped Project Admin creates projects, appoints a Project Manager, and invites the team. Invited people can register a new account or sign in to an existing account using the invited email address. They then accept the company invitation.

The company stores a plan tier and project and team limits. Project creation and team invitations enforce those limits. Billing and tier changes are outside this implementation; limits are provisioned in the company record.

The V45 migration places existing development accounts and projects in a `legacy-chronos` company. Existing data is test data; the migrated legacy memberships allow the development database to start while new company onboarding uses scoped assignments.
