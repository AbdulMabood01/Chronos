# Email alerts

The envelope button beside the theme control opens email preferences. Each active user can save a master switch and separate switches for announcements, timesheets, vacation, letters, reports, feedback, and performance. Preferences default to enabled and persist on the user's account. Turning off the master switch preserves category choices. In-app notifications and account invitations remain available.

`GET` and `PUT /api/notifications/email-preferences` always use the authenticated account. PUT accepts the eight boolean fields `enabled`, `announcements`, `timesheets`, `vacation`, `letters`, `reports`, `feedback`, and `performance`.

## Events and recipients

| Category | Events | Recipients |
| --- | --- | --- |
| Announcements | Publication, published updates, scheduled publication when visible | Active users |
| Timesheets | Submission, approval, rejection, reopening/change requests, hour edits, existing reminders | Existing workflow recipients; hour edits go to the owner |
| Vacation | Submission, approval, rejection, request edits | Existing workflow recipients; edits go to the owner |
| Letters | Submission, approval, rejection | HR reviewers on submission; requester on decisions |
| Reports | Submission and review/status updates | Active HR Admins; nonanonymous reporters also receive update notices with their receipt ID and status |
| Feedback | Submission | Recipient only |
| Performance | Publication and edits after publication | Reviewed employee |

Events follow the existing workflows: feedback has no approval/rejection step, reports use their existing review statuses, and performance drafts remain private. Anonymous reports never create email records identifying the reporter. Emails do not include report contents, private review text, feedback text, or feedback author identities.

## Delivery and configuration

Flyway migration `V33__email_alerts.sql` adds preferences, a persistent outbox, and announcement version tracking. Already published announcements are marked as handled during migration, avoiding a deployment-time replay of the existing feed.

Use the existing SMTP settings: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, and `CHRONOS_APP_URL` (see `.env.example` and `application.properties` for TLS options and the app URL variable). No new provider is required. Configure a reachable app URL for email links.

Workflow alerts are queued in the same transaction as the change, so rolled-back changes produce no email. The dispatcher polls every 30 seconds, processes up to 25 messages per pass, and retries mail failures up to five total attempts with a five-minute delay. It checks current preferences and active-account status again before delivery. With no SMTP sender/from address, queued alerts wait for configuration. Delivery is at least once: a process crash after SMTP acceptance but before database commit can cause a duplicate.

The existing scheduled timesheet reminder sender also honors the master and timesheet switches and is excluded from the new dispatcher to prevent duplicate reminders. Invitations are unaffected.

Optional Spring properties: `chronos.email-alerts.poll-ms` changes the polling interval; `chronos.email-alerts.enabled=false` disables the dispatcher (useful for tests). Inspect `email_alert_outbox` rows with `completed_at IS NULL AND attempts >= 5` for exhausted retries; logs include the outbox ID and attempt without recipient addresses or private content. Completed rows include delivered and preference-suppressed notices.

## Verification

Frontend tests cover category/master toggles, persistence, errors, keyboard dismissal, dark mode, and mobile layout. Backend tests cover category routing, opt-outs, retries, privacy, and account isolation. The PostgreSQL integration tests are opt-in:

```powershell
mvn '-Dtest=EmailAlertDatabaseTest' '-Dchronos.email.integration=true' test
```

Integration records roll back; Flyway migrations persist. Mail delivery is disabled in that test context.
