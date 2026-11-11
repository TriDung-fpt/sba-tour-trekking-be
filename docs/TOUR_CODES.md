# Tour codes & booking codes

Migration: `V35__tour_codes.sql` (table `trekking.tour_codes`).

## Rules

- A tour has at most one active code; codes are `A-Z0-9`, 2–20 characters, stored upper case.
- Codes are unique forever: a deleted or renamed code is kept and can never be reused, so old booking codes stay unambiguous.
- Rename = retire the old code (with the reason) + create a new one. Delete = soft delete, reason required.
- Every change is written to `audit_logs` with `entity_type = 'tour_code'`
  (`TOUR_CODE_CREATED`, `TOUR_CODE_RENAMED`, `TOUR_CODE_DELETED`).
- Tour list and detail responses include `code` (null when the tour has no active code).

## Booking codes

`<TOUR CODE>-<10 random chars>`, e.g. `FAN01-K7PQ2M9XRA`. Tours without a code use `BOOKING_CODE_PREFIX`
(env, default `BK`): `BK-K7PQ2M9XRA`.

## Admin API (`TOUR_READ` to read, `TOUR_MANAGE` to change)

| Method | Path | Body |
|---|---|---|
| GET | `/api/v1/admin/tour-codes?tourId=&includeDeleted=false&keyword=&page=0&size=20` | — |
| GET | `/api/v1/admin/tour-codes/{id}` | — |
| POST | `/api/v1/admin/tour-codes` | `{"tourId":1,"code":"FAN01"}` |
| PUT | `/api/v1/admin/tour-codes/{id}` | `{"code":"FAN02","reason":"Rebrand"}` |
| DELETE | `/api/v1/admin/tour-codes/{id}` | `{"reason":"Tour discontinued"}` |
| GET | `/api/v1/admin/tour-codes/tours/{tourId}/logs` | — |

Errors: `409 TOUR_CODE_ALREADY_ASSIGNED`, `409 TOUR_CODE_EXISTS`, `409 TOUR_CODE_DELETED`, `400 TOUR_CODE_UNCHANGED`.

## Schedule date validation

Creating or rescheduling a departure requires `startsAt` in the future (`400 INVALID_SCHEDULE_START`) and
`balanceDueAt` not in the past (`400 INVALID_BALANCE_DUE_AT`). A departure that already started cannot be
changed (`409 SCHEDULE_ALREADY_STARTED`); one with held or confirmed seats was already locked
(`409 SCHEDULE_NOT_EDITABLE`).
