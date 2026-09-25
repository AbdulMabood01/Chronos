# Confidential employee reports

Employees can now read their own identified reports through `GET /api/employee-reports/mine?page=0` and `GET /api/employee-reports/mine/{id}`. Both endpoints verify the active database user, constrain queries by reporter ID, exclude anonymous reports, and return `Cache-Control: no-store`. Missing and unowned report IDs both return 404. Management reads, reviews, and attachments remain Admin-only.

Submitted Reports refreshes after submission, every 15 seconds while visible, and on window focus. The dedicated employee detail view uses the same progress component as HR and shows chronological status updates with Actions Taken and Resolution Details. It excludes internal notes, staff identities, and view/download audit events. Anonymous reports remain receipt-only because they have no account link; the submission form and receipt explain this limitation.

Flyway V35 adds `employee_visible`, defaulting to false for all historical entries. HR must explicitly select “Share actions and resolution with the employee” for a new review entry to publish its actions/resolution. Internal notes are never published. Visibility is inserted atomically with the review event. Existing private text is not exposed retroactively.

Employees submit at `/workplace-reports`; only current active `ADMIN` users can access the management page at `/reports` and the `/api/employee-reports` read/review/download API. The old `/employee-reports` UI URL redirects to `/reports`. Timesheet exports are now at `/time-reports`. Project permissions and JWT role claims never grant report access. The service checks the database role before report queries. Application database credentials remain privileged; do not distribute them to employees or managers.

Flyway V29 stores reports, private attachments, and append-only application audit events in separate tables. They are excluded from general audit feeds, exports, and notifications. No report notification is sent; HR checks the dedicated management page. Anonymous submissions persist no reporter foreign key and no submitting actor in audit history. A database constraint prevents anonymous reports with an attached reporter ID. Receipt IDs are UUIDs and do not authorize lookup.

The management list reads the same database records created by submission and refreshes every 15 seconds while visible, on window focus, and when filters change. Filters cover full/partial Report ID, category, status, UTC submission date range, and anonymous/identified. Newest reports appear first. No synchronization job is required. No priority field exists in the current report model.

Flyway V30 adds last-updated timestamps, explicit status on each tracker entry (backfilled for existing history), and a flag for processed attachments. Last-updated changes when HR edits a case, not when someone views it. The tracker shows the acting administrator, status, note, and timestamp in chronological order. Internal notes have no employee-facing retrieval endpoint.

The submission form explains retained timestamps, processed anonymous attachments, visible self-identification, and possible infrastructure correlation. Infrastructure operators and database/backup administrators can access retained data. Configure TLS, restricted database/backup access, storage encryption, and an organizational retention policy for deployment. Avoid enabling HTTP payload, JDBC parameter, or proxy body logging for this feature. Application web/JDBC log levels default to INFO; submission/review record string representations are redacted.

Attachments: up to five files, 10 MB each, 20 MB combined, including after processing. Identified reports accept images, PDF, DOC/DOCX, TXT and ODT with original metadata. Anonymous reports accept still PNG/JPG/GIF, unencrypted PDF (up to 30 pages), and validated plain UTF-8 text. Images are re-encoded into fresh PNGs; PDFs are rendered at 120 DPI into fresh image-only PDFs, removing hidden authors, metadata, comments, scripts, links, and embedded files. Filenames are generated. Only processed copies are persisted for new anonymous reports. Rendering has pixel limits. Word/ODT/WebP must be exported to a supported format before anonymous submission.

Older anonymous files retain original bytes in database storage, but their filenames are never returned to HR and their downloads are sanitized on demand. Unsupported old files are withheld, not returned raw. Visible identifying content cannot be removed automatically and must be reviewed by the submitter. Previously downloaded files cannot be recalled. This provides anonymity from account/hidden attachment metadata in the HR interface, not a guarantee of untraceability.

Every download requires a current Admin account and the matching report ID. Downloads use attachment disposition, octet-stream, nosniff and no-store. There is no malware scanner; the HR page discloses this.

Status transitions proceed one step at a time. Resolving requires actions taken and resolution details. Internal notes/actions/resolution are immutable audit entries; closed reports reject further changes. Row locks serialize concurrent reviews. Views and downloads also produce private audit entries.

Validation:

```
mvn -Dtest=EmployeeReportAccessTest,EmployeeReportServiceTest,AnonymousReportAttachmentTest test
mvn -Dchronos.reports.integration=true -Dtest=EmployeeReportDatabaseTest test
```

The opt-in integration test uses the configured PostgreSQL database, applies pending Flyway migrations, and rolls back its test records. The default run skips it.
