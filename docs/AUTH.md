# Authentication

Auth uses the existing account entities and PostgreSQL tables. OTPs, refresh
token hashes, rate limits and session metadata are stored in Redis. Google sign-in
uses a unique, nullable `app_accounts.google_subject` to persist Google's stable
`sub` identifier.

## Database upgrade

For an existing database, apply `schema/migrations/001_add_google_subject.sql`
then `schema/migrations/002_add_account_session_version.sql` and
`schema/migrations/003_add_cooperation_policy_terms.sql` once before starting
this backend version. For example, with the Compose
database (replace the username and database with your settings):

```powershell
Get-Content schema/migrations/001_add_google_subject.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U your_database_user -d trekking_db
Get-Content schema/migrations/002_add_account_session_version.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U your_database_user -d trekking_db
Get-Content schema/migrations/003_add_cooperation_policy_terms.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U your_database_user -d trekking_db
```

The migration preserves existing accounts, passwords, roles and sessions.
Fresh Compose volumes and Testcontainers load the baseline followed by these
migrations. Restarting an existing volume does not execute init scripts;
apply the upgrade explicitly. The deployed baseline is kept unchanged.

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
| POST /google | `{"idToken":"<Google ID token>"}` | 200; app access JWT and refresh cookie |
| POST /refresh | No body; refresh cookie | 200; new access JWT and refresh cookie |
| POST /logout | No body; refresh cookie | 204; revoke session and clear cookie |
| POST /forgot-password | `{"email":"you@example.com"}` | 202, generic message |
| POST /reset-password | `{"email":"you@example.com","otp":"123456","newPassword":"NewPassword123!"}` | 200; all old sessions invalid |
| POST /activate-account | `{"email":"guide@example.com","otp":"123456","newPassword":"NewPassword123!"}` | 200; verify invitation and set initial password; login separately |
| GET /me | No body; `Authorization: Bearer <accessToken>` | 200; account and current roles |

Access responses contain `accessToken`, `tokenType`, `expiresIn` (seconds) and
`account`. Refresh tokens are never included in JSON. Do not send an expired
Bearer header to public endpoints or `/refresh`; use only cookies and CSRF there.
Clients must serialize refresh calls: simultaneous use of the same refresh
token is treated as replay and revokes the session, requiring login again.
The frontend now coordinates cookie-changing operations across same-origin tabs
with Web Locks; automatic restore/refresh requires that browser capability on
HTTPS or localhost. See the frontend `docs/MODULE_1.md` for its fallback behavior.

Guide/staff invitations use a separate activation purpose. Before activation,
unverified invitation accounts cannot use verify-email or reset-password to
bypass the initial-password flow. Existing verified accounts marked for a password
change can still complete the normal reset flow. Provisioning, agreement APIs,
migration 003 and release checks are described in
[Module 1 implementation](MODULE_1_IMPLEMENTATION.md).

## Google sign-in

Set `GOOGLE_CLIENT_IDS` to the comma-separated OAuth client IDs of your frontend
applications. Empty means Google sign-in is disabled (`503 GOOGLE_AUTH_UNAVAILABLE`),
while email/password authentication keeps working. The frontend obtains the
Google Identity Services `credential` and sends it as `idToken` to `/google`
using the same cookies and CSRF handshake as `/login`. This endpoint accepts
JSON from the frontend; it is not a Google redirect callback or an access-token
exchange. No Google client secret is required for ID-token verification.

The backend verifies RS256 signatures with Google's public keys, expiry, issuer,
audience, authorized party when supplied, subject and verified email before
opening a database transaction. Invalid tokens return `401 INVALID_GOOGLE_TOKEN`.
Google tokens are used only to sign in; protected endpoints accept the app's
JWT and require its Redis session. Token verification needs outbound HTTPS to
Google's public-key endpoint; a key-fetch failure returns 503.

Returning Google users are looked up by `sub`, so an email change does not create
a new account or alter their local email/profile/roles. A new Google identity
creates a verified customer account with a random BCrypt credential that is
never returned. It can also link to a locally verified, active password account
with the same email, preserving its password and roles. Unverified, disabled,
blocked or password-reset-required accounts cannot bypass those checks. An
email bound to another Google subject returns `409 GOOGLE_ACCOUNT_CONFLICT`.

Automatic creation/linking requires a Gmail address or a Google Workspace
identity with a signed `hd` claim. Google may report `email_verified=true` for
third-party emails whose ownership has changed; these unbound identities return
`403 GOOGLE_EMAIL_VERIFICATION_REQUIRED` and require a separate ownership/linking
flow. That additional flow is not exposed by this API. See
[Google's server-side verification guidance](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

Google sessions use the same refresh rotation, logout, account status checks and
password-reset revocation as password sessions. A Google-created customer can
use `/forgot-password` and `/reset-password` to set a local password.

## Code boundaries

`JwtService` is the token-issuance interface; `JwtServiceImpl` implements the
existing app JWT format. `GoogleTokenVerifier` is a separate interface for
external identity verification. `features.auth.validation` owns input
normalization, password constraints, OTP/session eligibility and login limits.
Repositories perform persistence and atomic Redis operations, returning results
for the validation layer to interpret. Redis infrastructure failures propagate
as data-access errors and map to 503 at the API/security boundary. The normalized
email lookup in `AppAccountRepository` supports legacy mixed-case rows; it does
not perform request validation. Database constraints remain in place for integrity.

Errors contain `code`, `message`, and `fieldErrors`. Common statuses: 400 invalid
input/OTP, 401 wrong credentials or token, 403 account eligibility/CSRF, 409 duplicate
email, 429 login throttled, 503 unavailable dependency. For unknown, ineligible or
throttled emails, forgot/resend deliberately return the same 202 response.
If registration returns `MAIL_UNAVAILABLE`, the unverified account has been saved;
configure/fix SMTP and use resend after the cooldown rather than registering again.

The previously documented `tools/check_smtp.py` is absent from this repository.
For SMTP failures, inspect the configured mail provider and backend diagnostics
without exposing credentials. Gmail's
`535 5.7.8 Username and Password not accepted` means SMTP authentication was
rejected. Check `MAIL_USERNAME` and create a fresh App Password for that same
Google account as described in [Google's instructions](https://support.google.com/accounts/answer/185833).
Update `MAIL_PASSWORD` in `.env`, then apply it with
`docker compose up -d --force-recreate --wait backend`.
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
- Account blocking increments a PostgreSQL session version in the same transaction
  as status and audit. Old access/refresh sessions remain invalid after unblock.
  See [Module 1 authorization](MODULE_1_AUTHORIZATION.md) for the new APIs and guards.
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

The suite also checks Google verification with real RSA signatures and a local
test key/JWKS response, and exercises Google account/session behavior with a
mocked `GoogleTokenVerifier`. The migration test creates a separate temporary
database on the Testcontainers PostgreSQL server and upgrades the unchanged
baseline with synthetic legacy accounts. These checks need no real Google
credential or access to Google's network; configure client IDs and use a real
frontend credential for a live Google sign-in check.

### Manual HTTP and Swagger checks

`docker-compose.api-test.yml`, `tools/test_api.py` and `tools/test_swagger.js`
are absent from this repository. Use the Maven suite above for automated
verification. Manual Swagger checks follow the documented CSRF/Bearer flow
with a disposable verified test account. Live SMTP and Google sign-in need
configured test credentials; they are not covered by mocked mail/token tests.
