# Password management

Change Password is available on Profile. Forgot Password is linked from the sign-in page.
Both enforce the existing activation policy: at least 12 characters, uppercase, lowercase,
and a number, with a maximum of 72 UTF-8 bytes. Confirmation must match and the new
password must differ from the current password.

Reset email uses the same SMTP configuration as invitations: `MAIL_HOST`, `MAIL_PORT`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, and `FRONTEND_URL` (HTTPS in production).
Links expire in 30 minutes; a new link replaces the previous link. Tokens contain 32
cryptographically random bytes and only SHA-256 hashes are persisted. The link secret
is carried in the URL fragment and removed from browser history when the page opens.

Migration V34 adds recovery fields and a credential version to users. Successful password
changes and resets increment that version, invalidating existing JWTs on their next request.
User row locks serialize changes and ensure a reset token cannot be used twice.

Forgot-password responses are identical for unknown, invited, inactive, and active accounts,
including email delivery failures. Check SMTP configuration if mail does not arrive;
never log reset tokens or email bodies. Requests have the existing per-IP authentication
rate limit and a one-minute email cooldown per account. For multiple application instances,
configure shared gateway rate limits as with login.

Endpoints: POST `/auth/forgot-password` (`email`), `/auth/reset-password/validate` (`token`),
`/auth/reset-password` (`token`, `newPassword`, `confirmation`), and authenticated
`/auth/change-password` (`currentPassword`, `newPassword`, `confirmation`).
