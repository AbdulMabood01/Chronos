# Feedback and quarterly performance reviews

Navigation: Feedback & Performance Reviews → Feedback / Performance Reviews.

Informal feedback is stored in `employee_feedback`. Authenticated active users can search active employees by name or email, submit feedback to another employee, and read their received or given history. The recipient projection never returns sender IDs or emails, and returns a null sender name for anonymous feedback. Sender IDs are retained privately to support the sender's own history. No notification or generic audit entry is generated for feedback. Manager/Employee classification is captured at submission from operations roles or project manager/approver assignments.

Official reviews use `performance_reviews`, unique per employee/year/quarter. Only the current database Super Admin role can create, edit, publish, or read audit history. Employees and project managers can read only their own published reviews. Publication is explicit; edits after publication become immediately visible and retain the original publication date. Employee and period are immutable after creation. Versions prevent stale saves or publication. `performance_review_audit` retains actor, timestamp, action and complete review snapshots for creation, edits and publication. Ratings are not implemented.

Migration: `V31__feedback_and_performance_reviews.sql` (applied by Flyway on backend startup).

API base: `/api/feedback-reviews`. All responses use `Cache-Control: no-store`.

- `GET /employees?query=...`: minimal employee search results (up to 30).
- `POST /feedback`: employeeId, content, optional category, anonymous.
- `GET /feedback?given=false`: received history; `given=true`: sender history.
- `GET /reviews`: own published history; Super Admin may pass employeeId and see drafts too.
- `POST /reviews`, `PUT /reviews/{id}`: create or edit review fields, year, quarter, employeeId, version.
- `POST /reviews/{id}/publish?version=...`: publish a saved draft.
- `GET /reviews/{id}/audit`: Super Admin audit records.

Checks: `mvn -Dtest=FeedbackReviewAccessTest test`; opt-in local PostgreSQL tests: `mvn -Dtest=FeedbackReviewDatabaseTest -Dchronos.feedback.integration=true test`. Database test records roll back; Flyway schema migrations persist. Frontend: `node node_modules/vitest/vitest.mjs run src/pages/FeedbackReviews.test.jsx`; browser: `node node_modules/@playwright/test/cli.js test feedback-reviews.pw.cjs`.
