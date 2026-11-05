# Backend context map

This is an orientation map, not a replacement for source. Verify current behavior in the repository before changing anything.

## Ownership map

- `features/auth`: login, registration, verification, password recovery, sessions, CSRF.
- `features/account`: account profiles, roles, account administration, authorization view.
- `features/tour`: tours and their public/admin lifecycle, activities, schedules/defaults, media, tour terms, catalog/configuration of equipment attached to tours.
- `features/booking`: schedule availability, booking holds/lifecycle, passengers, customer/company booking views.
- `features/payment`: payment initiation and provider webhook handling.
- `features/equipment`: inventory, reservation, issue/return operations. Equipment catalog and tour rental configuration also have controller/API code in `features/tour`; search both before adding endpoints or entities.
- `features/policy`: policy content and immutable versions, approvals/revocations, availability, extraction.
- `features/invitation`: staff/guide account invitations and activation.
- `core/security`: application-wide security, authentication integration, role/permission enforcement. A feature-local config must not create a competing global `SecurityFilterChain`.

Follow backend `AGENTS.md` for package and layering rules. Trace Controller → service interface/implementation → repository → entity/schema. Don't put persistence logic in controllers.

## Database sources of truth

Read `schema/schema.sql`, every migration after it, the Compose/Testcontainers migration wiring, and `schema/migrations/README.md`. Bootstrap SQL is for a fresh database; migrations define later evolution. Verify actual schema names and the sequence currently wired into Compose/tests. The migration README has historical sections and may lag the migration files; don't infer that its first migration list is complete. Never edit an already-applied migration or reset a data volume to make a feature work.

## Existing table meanings

Use the existing concepts before proposing a new table:

| Domain | Existing tables | Meaning / boundary |
| --- | --- | --- |
| Identity | `app_accounts`, `account_roles`, `company_members`, `guides` | One login/account identity with assigned roles; company membership and guide profile are separate associations/profiles. Do not add separate customer/admin login tables. |
| Company and policy | `companies`, `policies`, `policy_versions`, `company_agreements`, `policy_version_revocations` (migration 015) | Company records; a policy is the logical document, versions hold content/history, agreements record a company's accepted version/snapshot, revocations record a version being withdrawn. |
| Tour catalog | `locations`, `tours`, `tour_locations`, `tour_media`, `tour_activities`, `tour_itinerary_days` (016), `tour_terms` (017), `tour_questions`, `tour_answers` | Tour is the product; location associations, media, itinerary/activity, terms, and Q&A are subordinate concepts. A tour is not a departure/date instance. |
| Schedule and guides | `tour_schedules`, `schedule_guides`, `tour_schedule_defaults` (020) | A schedule is a dated departure with capacity/pricing and assigned guides. Defaults are for creating schedules; do not use them as a replacement for an actual schedule. |
| Tour equipment/rental | `tour_equipment_packages`, `tour_required_items`, `tour_equipment_items` (007/021), `equipment`, `rental_package_templates`, `rental_package_template_items` (007/009) | Separates equipment catalog, reusable rental package templates, equipment configured for a tour, and required items. Check latest migration 021: tour equipment packages/items evolved from the earlier single-combo model. |
| Booking | `carts`, `bookings`, `participants` | Cart/selection is temporary intent; booking is the durable reservation/price snapshot and lifecycle; participants are the travelers attached to a booking. Do not create a second booking-like record without proving a distinct lifecycle. |
| Inventory operations | `equipment_categories`, `equipment_inventory`, `equipment_reservations`, `equipment_reservation_items`, `equipment_handover_records`, `equipment_handover_items` (014) | Catalog taxonomy and stock are distinct from booking/tour rental choices; reservations and issue/return handover records track operational inventory. |
| Payments/refunds | `payment_attempts`, `payment_notices`, `customer_payments`, `refund_requests`, `customer_refunds` | Attempts are provider payment requests; notices are received webhook events; customer payments are recorded receipts; refund request is a decision workflow; customer refund is the actual recorded refund. Preserve idempotency and auditability. |
| Company settlement | `company_statements`, `company_statement_items`, `company_transfers` | Statement is a settlement period/snapshot; items are its calculations/adjustments; transfer is money sent against a statement. These are not interchangeable with customer payment/refund records. |
| Reviews/support | `reviews`, `review_votes`, `support_requests`, `support_messages` | Reviews belong to a completed customer experience; votes are per-account reactions. Support request is a case and messages are its conversation. |
| Assistant/audit | `ai_reference_documents`, `chats`, `chat_messages`, `chat_message_sources`, `audit_logs` | Uploaded reference material, chat/thread, messages/source citations, and immutable actor/action history. Search these before creating generic notes, message, or history tables. |

Migrations also create `account_financial_permissions`, legacy/archive structures, and other historical tables. Check each migration before adding a similar concept. The bootstrap `schema.sql` comment says 39 tables but later migrations add tables, so that count is not the current total.

## API and schema change gate

Before adding an endpoint or table:

1. Search controller mappings, services/repositories/entities, current schema plus all migrations, and the frontend `features/*/api` callers/types.
2. Identify whether an existing endpoint/table can represent the use case without overloading its lifecycle or meaning.
3. If a new API/table is still needed, state the concrete missing behavior, lifecycle, ownership, constraints, and why existing concepts do not fit. Then add only the minimal contract/schema change.
4. For endpoints, preserve backend authorization and request validation. For schema changes, add a new ordered migration and update all fresh-database wiring/docs that enumerate migration scripts.
