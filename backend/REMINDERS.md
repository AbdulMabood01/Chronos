# Timesheet submission reminders

Users can disable reminder emails using the Email alerts master switch or the Timesheets category beside the theme control. Their in-app reminders remain enabled. See [Email alerts](EMAIL_ALERTS.md).

Active employees and admins with a missing, draft, or rejected monthly timesheet receive an email to their work address and an in-app notification at 8:00 PM every Friday and on the last calendar day of the month. Submitted, approved, locked, and change-requested timesheets and Admins are excluded.

Employees choose an IANA timezone in their profile. Existing and new accounts default to America/Chicago until changed. Dates and daylight-saving offsets are evaluated in that timezone. The scheduler checks every five minutes and retries failed deliveries until local midnight. A Friday that is also month-end produces one reminder. The Settings reminder toggle pauses both channels.

Configure these environment variables on the backend deployment:

```text
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your-smtp-username
SPRING_MAIL_PASSWORD=your-smtp-password
MAIL_FROM=chronos@example.com
CHRONOS_APP_URL=https://chronos.example.com
```

SMTP authentication and STARTTLS default to enabled. Configure MAIL_SMTP_AUTH and MAIL_SMTP_STARTTLS_ENABLE if your mail service requires different settings. SMTP uses Spring Boot's [mail configuration](https://docs.spring.io/spring-boot/reference/io/email.html). No credentials are stored in the repository. Missing SMTP configuration is logged as a delivery failure and does not mark reminders sent.

Flyway migration V26 adds the profile timezone and removes the obsolete final-week reminder settings. The backend must be running between 8 PM and midnight in each employee's timezone; reminders missed for an entire evening are not backfilled. Delivery is recorded after SMTP accepts the message. SMTP and database commits are not atomic: a process crash or database failure immediately after SMTP acceptance can cause a duplicate on retry. SMTP acceptance does not guarantee inbox delivery.

Validation uses mocked email delivery; no real emails are sent by tests.
