# Contact inquiries

Contact us is available from the landing-page header and footer and beneath the login form. The popup uses Maxwell styling, supports Escape/close/backdrop dismissal and keyboard focus wrapping, and fits mobile viewports.

## Behavior

The form collects name, company, email, optional phone and a message. Company contact details identify Maxwell IT Solutions in Chicago, Illinois, USA. Fields have browser and server validation. Closing and reopening the same entry point preserves an unfinished draft in component memory. No inquiry details are written to local/session storage or the database. A successful send clears the draft; reopening starts a fresh inquiry.

GET /api/contact/status returns only an available boolean, with no inbox or SMTP details. POST /api/contact/inquiries validates the form and sends a plain-text email using the existing Spring SMTP configuration. It uses the configured sender, a fixed subject and the visitor's validated email as Reply-To. Success is returned only after SMTP accepts the message; failures preserve the form for retry and show no SMTP provider details. There is no automatic scheduling or confirmation email to the visitor.

Until the destination inbox, SMTP host and sender are configured, readiness is false and Send inquiry is disabled. A direct POST also returns 503 without sending or storing the inquiry. An unavailable backend shows an unavailable notice instead of a success message.

## Enable later

Set CONTACT_INQUIRY_TO to the destination inbox in the backend environment. This setting is deliberately blank in .env.example. Reuse the existing MAIL_HOST, MAIL_PORT, MAIL_FROM, MAIL_USERNAME, MAIL_PASSWORD and SMTP transport settings. No destination address or secret is compiled into the frontend.

Restart the backend after configuring these values. The frontend checks readiness each time the popup opens. Backend changes also require the normal backend restart/deployment; an older running backend without the contact endpoints will show the unavailable state.

The email contains the submitted fields and whether the inquiry came from the landing or login page. Reply directly to it to arrange a call or other follow-up.

## Basic spam controls

The backend rejects a filled hidden website field and limits submissions to five attempts per remote IP per fifteen minutes, with a bounded per-process client map and Retry-After on rejection. Client-supplied forwarded headers are not trusted by the limiter. For multiple backend instances, configure equivalent shared gateway limits. Public security access is limited to GET status and POST inquiries; other contact methods remain protected.

## Verification

Backend tests: ContactInquiryServiceTest, ContactInquiryAccessTest and ContactRateLimitFilterTest cover unconfigured delivery, reply-to/header validation, SMTP failures, public access, request validation and throttle windows. Mail senders are mocked; no actual email was sent.

Browser tests: frontend/browser-tests/contact.pw.cjs cover landing/login/footer entry points, draft retention, disabled unconfigured submissions, single submission, successful reset, error/retry handling, validation, keyboard focus and mobile layout. Existing landing browser and login/API/permission unit tests were also run.
