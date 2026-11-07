# Tour publication

Administrators create a cancellation policy using `POST /api/v1/admin/policies` with `kind` set to `cancellation`, then publish a version using `POST /api/v1/admin/policies/{id}/versions` with `content` and ISO-8601 `effectiveFrom`. The admin UI provides these operations under Chính sách hủy. `GET /api/v1/admin/policies/cancellation/available` lists the latest effective version of each active cancellation policy.

To publish a tour, send its selected `cancellationPolicyVersionId` in `PUT /api/v1/tours/{id}` with `status: "PUBLISHED"`. The service validates that the version is available and records the publishing admin and time. Multiple policies can exist; each tour selects one. Bookings snapshot the assigned version. If publication fails, the tour remains a draft and can be edited and retried. Policy endpoints require the policy management permission.

Deleting a tour changes its status to `ARCHIVED` and hides it from the default tour list and public listing; existing schedules and bookings remain in the database.

## Policy lifecycle

`POST /api/v1/admin/policies/extract-document` accepts a multipart `file` (DOC, DOCX or text-based PDF, maximum 5 MB) and returns `{ "content": "..." }`. The admin reviews and edits the extracted text before saving a version. The source document is not stored. Scanned PDFs without selectable text require OCR outside the application. The endpoint requires policy management permission.

- `POST /api/v1/admin/policies/with-version`: atomically create a policy and its first version. Body: `{ "policy": { "code": "CANCEL-STANDARD", "title": "Standard cancellation", "kind": "cancellation" }, "version": { "content": "Approved terms", "effectiveFrom": "2026-11-01T00:00:00+07:00" } }`.
- `PATCH /api/v1/admin/policies/{id}/status`: `{ "active": false, "reason": "Replace terms" }` stops the policy immediately. `active: true` re-enables it; revoked versions remain revoked.
- `POST /api/v1/admin/policies/{id}/versions/{versionId}/revoke`: `{ "reason": "Incorrect terms" }` permanently cancels that version, including before its effective date. Content remains immutable; actor, time and reason are retained separately.
- A canceled scheduled version does not displace the current version. Revoking the current version does not silently restore older terms. Add a replacement version explicitly. Its effective date must follow the last non-revoked version; canceled future versions do not block an earlier replacement date.
- Disabled policies and revoked versions cannot be selected for publication or used for new bookings. Existing booking snapshots remain unchanged. Tours retain their original policy association until the admin explicitly selects another version.

The admin page provides search by name/code, status filters, pagination and policy details with version history. Create uses one form; code can be generated automatically. The duplicate-name warning is advisory; codes are checked without case sensitivity by the service. Same names may represent different policies.

Flyway applies the policy-revocation migration during backend startup. Legacy
databases need a verified one-time baseline before startup; follow the backup
and migration procedure in `schema/migrations/README.md`. No existing policy
content or booking snapshot is rewritten by the migration.
