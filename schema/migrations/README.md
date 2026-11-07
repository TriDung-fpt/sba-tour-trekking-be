# Database migrations

Flyway owns schema changes. The application applies pending migrations during
startup, before Hibernate validates entity mappings. A failed connection or SQL
statement stops startup and Flyway logs the migration version and root database
error. Hibernate remains `ddl-auto=validate`; it never repairs the schema.

## New databases

Run the normal Compose command:

```powershell
docker compose up -d --build --wait
```

The backend waits for PostgreSQL health, Flyway applies `V1` through `V26`, and
then Hibernate checks the resulting schema. Do not mount SQL into
`docker-entrypoint-initdb.d`; Compose and the app now share Flyway's one source
of truth.

## Existing databases (one-time adoption)

Do not enable automatic baselining blindly. First back up the database and
inspect its schema against the migration registry. Flyway has no history for
databases created by the old init-script process, so choose the version that
matches the schema already applied:

| Old SQL state | Flyway baseline version |
| --- | ---: |
| `schema.sql` only | 1 |
| through old migration `001` | 2 |
| through old migration `012` | 13 |
| through old migration `022` | 23 |
| through old migration `024` (current legacy schema) | 25 |

Old migration `023` was not present in the tracked migration chain. Baseline at
the last migration that actually ran, not the highest number someone intended
to run. A partial or uncertain schema needs manual review before adoption.

After verifying the version, set these two values temporarily in `.env`:

```dotenv
FLYWAY_BASELINE_ON_MIGRATE=true
FLYWAY_BASELINE_VERSION=25
```

Replace `25` with the verified version, then start the backend once:

```powershell
docker compose up -d backend
docker compose logs -f backend
```

Confirm Flyway baselined the expected version and applied later migrations.
Then set `FLYWAY_BASELINE_ON_MIGRATE=false` again. The recorded history stays
in `trekking.flyway_schema_history`; future startup runs only pending versions.
Never baseline an empty database or use baseline as a substitute for missing
schema changes.

## Adding migrations

- Add a new immutable SQL file under `src/main/resources/db/migration` using
  Flyway naming: `V<next>__<short_description>.sql`.
- Check all existing versions before choosing the next number. Two concurrent
  branches may choose the same version; resolve that before merge by assigning
  the later integrated migration a new version and updating its references.
- Never edit or rename a migration that has been merged or applied. Add a new
  migration to correct it.
- Keep migrations transactional where PostgreSQL supports it; do not add manual
  `BEGIN`/`COMMIT` wrappers. For non-transactional DDL, document why and use an
  explicit Flyway execution strategy.
- Do not edit `schema/schema.sql` as the way to upgrade an existing database.
  It is retained as historical bootstrap input for `V1`.
- CI starts an empty PostgreSQL container and lets the application run Flyway;
  migration version collisions or invalid SQL fail verification.

## Migration mapping

`V1` is the historical `schema.sql` baseline. Legacy scripts `001` through
`022` map to Flyway `V2` through `V23`; legacy `024` maps to `V25`. `V24` is
reserved for the gap in the old chain. `V26` enables pgvector in the `trekking`
schema. Future migrations start at `V27`.

Migration `006` archives and removes company-domain data. Back up first and
review its preflight behavior before applying it to any database that has not
already reached that version. Never reset a persistent volume to "fix" a
migration failure.
