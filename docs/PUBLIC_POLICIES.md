# Public footer documents

`GET /api/v1/public/policies/{code}` is the only anonymous policy endpoint.
Allowed codes: `BOOKING_GUIDE`, `REFUND_POLICY`, `WEBSITE_TERMS`, `PRIVACY_POLICY`, `LIABILITY_WAIVER`.
All write methods and every `/api/v1/admin/policies/**` endpoint retain existing authentication and `POLICY_MANAGE` checks.

The public response contains only `code`, `title`, `content`, `versionNumber`, and `effectiveFrom`.
It returns the current approved, effective version of an active, non-deleted policy, with `Cache-Control: no-store`.
Unknown, missing, draft, scheduled, disabled, deleted, and revoked documents return 404.
Revoking the current version does not restore an older version. Revoking a future version does not hide the currently effective version.

## Reuse existing policy records

By default each document maps to a policy with the same `code`. To expose an existing record without duplicating or changing its content,
set the corresponding server environment variable to its existing policy code:

| Public document | Environment variable |
| --- | --- |
| BOOKING_GUIDE | PUBLIC_POLICY_BOOKING_GUIDE_CODE |
| REFUND_POLICY | PUBLIC_POLICY_REFUND_POLICY_CODE |
| WEBSITE_TERMS | PUBLIC_POLICY_WEBSITE_TERMS_CODE |
| PRIVACY_POLICY | PUBLIC_POLICY_PRIVACY_POLICY_CODE |
| LIABILITY_WAIVER | PUBLIC_POLICY_LIABILITY_WAIVER_CODE |

Only map documents approved for public display. The mapping is server controlled; callers cannot request arbitrary policy IDs or codes.
An administrator still creates/publishes/withdraws these documents through the existing policy management API.
No database migration or seeded legal text is required.

Frontend links point to `/chinh-sach/{slug}`. These pages read the public API without attaching a bearer token.
Missing documents show an unavailable state rather than fabricated legal content. Backend restart is required after deployment/config changes.

Validation: `./mvnw test` includes `PublicPolicyIntegrationTests`, covering anonymous reads, all five mappings, version lifecycle,
data minimization, guest write denial, customer/guide admin denial, and retained administrator publication/withdrawal.
