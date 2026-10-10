# Contact inquiry validation

Implemented the landing-header/footer and login Contact us popup, with name, company, email, optional phone and message inputs. The destination inbox remains unset. The backend uses existing SMTP configuration and enables sending only when the destination, sender and SMTP host are configured. No real email was sent during this work.

Validation completed:

- Production frontend build passed. The existing large-bundle warning remains.
- 13 backend tests passed: six mail-service tests, four public-endpoint/security/validation tests, and three rate-limit tests. Email delivery was mocked. Backend compilation succeeded through the Maven test run.
- 34 frontend unit regressions passed: login, permission routing and API behavior, including cancelled readiness checks not reporting a connection loss.
- 21 browser tests passed: eight contact-flow tests and thirteen existing landing tests. Contact coverage includes both pages, footer entry, unavailable inbox with no POST, draft retention, single submission, successful reset, failed delivery/retry, native validation, keyboard focus wrapping, mobile dimensions and close-during-loading behavior. Ready/unconfigured/delivery responses were mocked.
- Desktop, mobile and login popup screenshots were visually inspected: contact-popup-desktop.png, contact-popup-mobile.png and contact-login-desktop.png. Header buttons fit at 320px after the mobile-spacing adjustment.

Actual SMTP delivery awaits CONTACT_INQUIRY_TO and existing MAIL_* configuration. Backend endpoints require normal restart/deployment before use by an older running backend; the existing live backend was not restarted. No database migration or inquiry persistence was added. Configuration and behavior are documented in docs/contact-inquiries.md.
