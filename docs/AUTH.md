# Authentication

Auth uses the existing account entities and PostgreSQL tables. OTPs, refresh
token hashes, rate limits and session metadata are stored in Redis. No database
migration is required.

## Local setup

Set up `.env` as described below, then build and run the backend, PostgreSQL and
Redis together (Docker is sufficient; a host JDK is not required):

```powershell
docker compose up -d --build --wait
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

For a host JVM instead, start only the dependencies with
`docker compose up -d postgres redis`, then run this command using JDK 21:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Duser.timezone=Asia/Ho_Chi_Minh'
```

In IntelliJ, select JDK 21, the project root as the working directory, and set VM
option `-Duser.timezone=Asia/Ho_Chi_Minh`. The Docker backend uses the same timezone.
Open `http://localhost:8080/swagger-ui/index.html` to try the API. Set `BACKEND_PORT`
to change its host port. Container database/Redis connections use service names
and internal ports, independently of host port mappings in `.env`.

## Request flow

All endpoints below are under `/api/v1/auth`. Before POST requests, call
`GET /csrf`, keep its `XSRF-TOKEN` cookie, and send the response's `token` in the
`X-XSRF-TOKEN` header. Keep cookies enabled in Postman. Swagger automatically
calls `/csrf` before each auth POST and sends the returned token with its cookie;
you can use Try it out / Execute immediately. `/csrf` requires no input parameters.

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

To diagnose SMTP using the running container's JavaMail libraries without sending
email, run `python tools/check_smtp.py` (requires a host JDK and Maven). Credentials
are read inside the container and are redacted from diagnostic output. Gmail's
`535 5.7.8 Username and Password not accepted` means SMTP authentication was
rejected. Check `MAIL_USERNAME` and create a fresh App Password for that same
Google account as described in [Google's instructions](https://support.google.com/accounts/answer/185833).
Update `MAIL_PASSWORD` in `.env`, then apply it with
`docker compose up -d --force-recreate --wait backend` and repeat the diagnostic.
After authentication succeeds, call `POST /api/v1/auth/resend-verification` with
the email from the failed registration (allow 60 seconds after the previous OTP
request). The endpoint deliberately returns a generic 202 response; confirm that
the email arrives before using its code in `POST /api/v1/auth/verify-email`.

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

### Live Docker API checks

Use the local Mailpit override to test all ten auth endpoints over HTTP, including
email verification, refresh rotation/replay, logout, password reset, validation,
CSRF and CORS. This override captures SMTP locally instead of sending real email:

```powershell
docker compose -f docker-compose.yml -f docker-compose.api-test.yml up -d --build --wait
python tools/test_api.py
```

Mailpit's inbox is at `http://localhost:8025`. The script needs Python 3, uses no
third-party packages, writes `target/api-test-report.json`, and creates one unique
disposable `api-test-...@example.invalid` account in the development database.
The override enables HTTP cookies and the local origin `http://localhost:5173`.
To return to the SMTP settings in `.env`, run `docker compose up -d --wait backend`.

### Swagger browser checks

`tools/test_swagger.js` is a Playwright MCP `browser_run_code_unsafe` filename
entry point. Open Swagger's login operation, select Try it out, and fill its JSON
body with a verified disposable test account before running the file. The suite
clears browser cookies and executes 20 checks through Swagger's buttons across
all ten auth endpoints, including the first POST without a manual CSRF handshake.
Credentials stay in memory. OTP endpoints use unknown accounts or invalid input;
these browser checks do not verify delivery through the SMTP credentials in `.env`.
