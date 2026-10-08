# Authentication

Auth uses the existing account entities and PostgreSQL tables. OTPs, refresh
token hashes, rate limits and session metadata are stored in Redis. No database
migration is required.

## Local setup

Use JDK 21 and start the services:

```powershell
docker compose up -d
```

Copy missing settings from `.env.example` into the ignored `.env`. The application
loads `.env` from its working directory. Set two independent random secrets
(`JWT_SECRET`, `OTP_SECRET`, at least 32 bytes each). Generate each value with:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Set Gmail `MAIL_USERNAME`, `MAIL_PASSWORD` (an App Password, not the normal Gmail
password) and `MAIL_FROM`. Enable two-step verification in the Gmail account to
create an App Password. Never commit these values. Use `AUTH_COOKIE_SECURE=false`
only for local HTTP. Leave `AUTH_ALLOWED_ORIGINS` empty until there is a frontend;
later set a comma-separated list of exact origins.

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Ho_Chi_Minh'
```

In IntelliJ, select JDK 21, the project root as the working directory, and set VM
option `-Duser.timezone=Asia/Ho_Chi_Minh`. Open `/swagger-ui/index.html` to try the API.

## Request flow

All endpoints below are under `/api/v1/auth`. Before POST requests, call
`GET /csrf`, keep its `XSRF-TOKEN` cookie, and send the response's `token` in the
`X-XSRF-TOKEN` header. Keep cookies enabled in Postman. Swagger is configured to
read the CSRF cookie after you execute `/csrf` once.

| Method/path | JSON body | Success |
| --- | --- | --- |
| POST /register | `{"email":"you@example.com","password":"StrongPassword123!","fullName":"Customer","phone":"0900000000"}` | 201; verification email |
| POST /verify-email | `{"email":"you@example.com","otp":"123456"}` | 200 |
| POST /resend-verification | `{"email":"you@example.com"}` | 202, generic message |
| POST /login | `{"email":"you@example.com","password":"StrongPassword123!"}` | 200; access JWT and refresh cookie |
| POST /refresh | No body; refresh cookie | 200; new access JWT and refresh cookie |
| POST /logout | No body; refresh cookie | 204; revoke session and clear cookie |
| POST /forgot-password | `{"email":"you@example.com"}` | 202, generic message |
| POST /reset-password | `{"email":"you@example.com","otp":"123456","newPassword":"NewPassword123!"}` | 200; all old sessions invalid |
| GET /me | No body; `Authorization: Bearer <accessToken>` | 200; account and current roles |

Access responses contain `accessToken`, `tokenType`, `expiresIn` (seconds) and
`account`. Refresh tokens are never included in JSON. Do not send an expired
Bearer header to public endpoints or `/refresh`; use only cookies and CSRF there.
Clients must serialize refresh calls: simultaneous use of the same refresh
token is treated as replay and revokes the session, requiring login again.

Errors contain `code`, `message`, and `fieldErrors`. Common statuses: 400 invalid
input/OTP, 401 wrong credentials or token, 403 account eligibility/CSRF, 409 duplicate
email, 429 login throttled, 503 unavailable dependency. For unknown, ineligible or
throttled emails, forgot/resend deliberately return the same 202 response.
If registration returns `MAIL_UNAVAILABLE`, the unverified account has been saved;
configure/fix SMTP and use resend after the cooldown rather than registering again.

## Session and OTP behavior

- Only customer self-registration is supported. Email is normalized, passwords
  are BCrypt-hashed, and email verification is required before login.
- OTP expires after 5 minutes, allows 5 failed attempts, and can be used once.
  Verification and reset purposes are separate. Requests are limited to one
  email per 60 seconds and five per hour/email/purpose, including unknown emails.
- Login is blocked after 10 failed attempts/email or 100 failed attempts/IP in a
  15-minute window. IP comes from the direct peer, not an untrusted forwarded
  header. Configure trusted proxy handling before deploying behind a proxy.
- Access tokens last 15 minutes, capped at the session's expiry. Sessions have
  an absolute lifetime of 7 days. Refresh rotates the token without extending
  this lifetime; replay of a previous token revokes its session.
- Redis stores OTP HMACs and refresh SHA-256 hashes, never raw codes/tokens.
  Every protected request checks session state and current account eligibility
  and roles. Logout takes effect immediately.
- A session's HMAC fingerprint of the account password hash is checked on every
  authenticated request/refresh. Password reset invalidates all old sessions
  when the PostgreSQL transaction commits, without a cross-database transaction.
  Old Redis keys disappear at their original TTL; they cannot authenticate.
- Redis scripts atomically consume OTPs and rotate tokens. Invalid OTP attempt
  counters persist even when the surrounding database transaction rolls back.
  If a database update fails after consuming a code, request another code.
- Redis outages fail closed with 503. Redis uses a persisted volume, AOF and
  `noeviction`; complete loss of its session data forces re-login. It is a single
  standalone instance here, not a Redis Cluster deployment.
- Redis is published only on loopback for local use. In production use a private,
  authenticated Redis connection, HTTPS cookies, strong secrets and exact CORS
  origins. Secret rotation invalidates existing tokens/OTP/session fingerprints.

## Tests

Docker must be running, then execute:

```powershell
.\mvnw.cmd test
```

Testcontainers starts separate PostgreSQL and Redis instances with random ports,
initializes PostgreSQL from the existing schema, and cleans up the containers.
Tests do not modify the development database. OTP mail delivery is mocked, so
the suite sends no real email and needs no Gmail credentials.
