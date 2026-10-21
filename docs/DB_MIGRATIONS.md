# Database migration rules

The backend uses Flyway versioned SQL migrations from `src/main/resources/db/migration`. Flyway applies pending migrations during backend startup and records checksums in `flyway_schema_history`. Hibernate remains in `validate` mode. The existing `schema/schema.sql` is the baseline for a new local database; on existing non-empty databases Flyway records that baseline as version `0` before applying `V1`.

## Version allocation and agent coordination

- Migration versions are sequential integers: `V1__name.sql`, `V2__name.sql`, and so on. Do not use dates, timestamps, branch names, or guessed numbers.
- Only the integration coordinator assigns the next number. The current highest assigned version is `V1`; the next unassigned version is `V2`.
- Agents working in parallel must put unnumbered drafts outside Flyway's configured location, under `schema/migration-drafts/`. Never add a guessed `Vn` file directly.
- The coordinator serially reviews drafts, allocates the next number, moves the approved script into `src/main/resources/db/migration`, updates this registry, and merges it. The version registry and migration are committed together.
- If two drafts overlap or conflict, the coordinator combines or orders them and assigns distinct versions. Do not solve a conflict by renumbering a migration already merged or applied.

| Version | Migration | Status |
| --- | --- | --- |
| 0 | Existing `schema/schema.sql` baseline | Baseline |
| 1 | `V1__partner_applications.sql` | Assigned |
| 2 | Next available; coordinator assignment required | Unassigned |

## Writing and changing migrations

- Use UTF-8 SQL and PostgreSQL syntax. Keep migrations focused, deterministic, and safe for existing data.
- Prefer additive, backward-compatible changes. For renames or removals, use an expand-and-contract rollout across separate releases.
- Before adding `NOT NULL`, uniqueness, or check constraints, make sure existing rows satisfy them or include an explicit, reviewed backfill.
- Never put destructive resets, demo/test seed data, secrets, or environment-specific values in a production migration.
- Once a versioned migration has been merged or applied to any shared database, treat it as immutable. Fix forward with a new migration.
- Do not run a Flyway-managed migration manually. Deploy the app with the migration; Flyway serializes concurrent application startup and tracks which versions ran.

## Existing database baseline

`spring.flyway.baseline-on-migrate=true` with baseline version `0` is for databases already initialized from the checked-in baseline schema. Do not point this application at an empty database and expect Flyway to build all existing tables: the baseline DDL remains the bootstrap for a new database. After Flyway has initialized a database, do not restore or replay the baseline over it.
