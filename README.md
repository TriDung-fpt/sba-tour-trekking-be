# WeTrek — Tour & Trekking Marketplace

WeTrek helps travelers discover trekking tours and connect with local tour operators. The project is split into two sibling repositories:

- `sba-tour-trekking-fe` — React + TypeScript web app (Vite).
- `sba-tour-trekking-be` — Java 21 + Spring Boot API, PostgreSQL, and Redis.

This repository is the backend. The frontend expects it at `http://localhost:8080` and uses the Vite `/api` proxy during local development.

## Product and current scope

- Guests can browse public tours and tour details.
- Customers can register, verify email, sign in, and update their profile.
- Tour operators apply with business details, verify their email, set their own password, and wait for an admin decision. Approval grants partner permissions; pending accounts cannot manage tours.
- Approved partners can manage tours and departures and view departure rosters. Admins can review partner applications and submitted tours.
- Customer booking and payment APIs are not yet part of the documented backend contract. Treat related screens as incomplete until the API contract says otherwise.
- The frontend currently supports Vietnamese and English. Guide and staff workflows are not fully implemented.

## Architecture at a glance

```text
Browser (React/Vite :5173)
  └─ /api proxy ──> Spring Boot API (:8080)
                       ├─ PostgreSQL — persistent accounts, companies, tours, schedules
                       └─ Redis — OTPs, refresh sessions, rate limits, activation tokens
```

The backend is organized by feature under `src/main/java/com/trekking/app/features/`. Keep controllers thin: controller → service interface → service implementation → repository. Request and response DTOs are Java records. Hibernate runs in `validate` mode; Flyway owns versioned schema changes.

## Start the full local app

Prerequisites: Docker Desktop, Node.js with npm, and access to both sibling repositories.

### 1. Configure and start the backend

From this repository:

```powershell
Copy-Item .env.example .env
```

Edit `.env` before starting:

- Set matching PostgreSQL values for `POSTGRES_USER`/`DB_USERNAME` and `POSTGRES_PASSWORD`/`DB_PASSWORD`.
- Set `JWT_SECRET` and `OTP_SECRET` to different random values, each at least 32 bytes. Do not commit `.env` or share these values.
- Configure SMTP using `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` if you need registration and partner activation emails. Legacy `EMAIL_SENDER` and `EMAIL_PASSWORD` are supported when the corresponding `MAIL_*` settings are omitted. For Gmail, use an App Password.
- Keep `AUTH_COOKIE_SECURE=false` only for local HTTP. `AUTH_ALLOWED_ORIGINS` should include the local frontend origin.

Start PostgreSQL, Redis, and the API:

```powershell
docker compose up -d --build --wait
```

The API is at `http://localhost:8080`; Swagger UI is at `http://localhost:8080/swagger-ui/index.html`.

### 2. Start the frontend

In a second terminal, from the sibling `sba-tour-trekking-fe` repository:

```powershell
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite forwards `/api` requests to the backend on port 8080. If port 5173 is occupied, use another Vite port and update `FRONTEND_URL` and `AUTH_ALLOWED_ORIGINS` in the backend `.env` to match. Do not terminate unrelated browser or application processes just to free a port.

## Run checks

Backend (JDK 21 and Docker are required; tests use Testcontainers):

```powershell
.\mvnw.cmd test
```

Frontend (run from `sba-tour-trekking-fe`):

```powershell
npm run lint
npm run build
```

## Database and migrations

- `schema/schema.sql` is the bootstrap schema for a new local PostgreSQL volume. Docker runs it only when creating a fresh volume.
- Flyway records that existing schema as baseline version `0`, then automatically applies pending migrations when the backend starts. Current migrations are listed in [`docs/DB_MIGRATIONS.md`](docs/DB_MIGRATIONS.md), including `V2__tour_refund_rules.sql`.
- Do not manually run Flyway migrations, change a migration after it has been applied, or edit `schema/schema.sql` to update an existing database.
- Migration versions are sequential. The next unassigned version is `V3`; only the integration coordinator assigns it. Parallel agents put unnumbered drafts under `schema/migration-drafts/` until the coordinator approves and numbers them.
- Full policy and version registry: [`docs/DB_MIGRATIONS.md`](docs/DB_MIGRATIONS.md).

## API and feature documentation

- [`docs/API_SPEC.md`](docs/API_SPEC.md) — shared backend/frontend API contract. Update the contract when endpoint paths, payloads, response shapes, or authorization change.
- [`docs/AUTH.md`](docs/AUTH.md) — local auth setup, CSRF, email verification, sessions, and partner activation.
- [`docs/DB_MIGRATIONS.md`](docs/DB_MIGRATIONS.md) — migration creation and coordination rules.
- [`AGENTS.md`](AGENTS.md) — backend architecture, security, and testing rules.

## Contributing

1. Read this file and the relevant `AGENTS.md` in each repository before changing code.
2. Agree on the API contract first when a task touches both frontend and backend. Implement and review the API producer and consumer against the same DTO and authorization rules.
3. Enforce permissions on the backend; frontend route hiding is only a usability layer.
4. Keep SQL, Java, and UI text UTF-8 so Vietnamese content remains readable.
5. For schema work, follow `docs/DB_MIGRATIONS.md`; never guess a migration number.
6. Run the checks for the repository you changed and include their results in your handoff.

Use small, focused changes. Never commit secrets, `.env`, generated `target/` or `node_modules/` output, or demo seed data as a production migration.
