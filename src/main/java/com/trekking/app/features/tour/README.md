# Tour and schedule CRUD (initial scope)

Tour is the reusable product (title, description, difficulty, duration and services).
TourSchedule is a departure of that product (dates, capacity, prices and deposit).
A Tour has zero or more schedules; each schedule belongs to exactly one Tour.
The existing `TourSchedule.tourId` writes the foreign key. Its lazy `tour` association
reads the same column with `insertable=false, updatable=false`. A reverse collection
on Tour is unnecessary: schedules are queried and paginated by tourId.

## Routes

| Method | Path | Result |
| --- | --- | --- |
| POST | /api/v1/tours | Create draft tour, 201 |
| GET | /api/v1/tours?page=0&size=20 | Paginated tour summaries |
| GET | /api/v1/tours/{tourId} | Tour details |
| PUT | /api/v1/tours/{tourId} | Replace editable tour fields and optionally set status |
| DELETE | /api/v1/tours/{tourId} | Delete unused draft, 204 |
| POST | /api/v1/tours/{tourId}/schedules | Create scheduled departure, 201 |
| GET | /api/v1/tours/{tourId}/schedules?page=0&size=20 | Paginated schedule summaries |
| GET | /api/v1/tours/{tourId}/schedules/{scheduleId} | Departure details |
| PUT | /api/v1/tours/{tourId}/schedules/{scheduleId} | Replace editable departure fields |
| DELETE | /api/v1/tours/{tourId}/schedules/{scheduleId} | Delete unused departure, 204 |
| GET | /api/v1/tours/{tourId}/activities | List the tour's activities by day and order |
| POST | /api/v1/tours/{tourId}/activities | Add an activity |
| PUT | /api/v1/tours/{tourId}/activities/{activityId} | Update one activity |
| DELETE | /api/v1/tours/{tourId}/activities/{activityId} | Delete one activity |
| POST | /api/v1/tours/{tourId}/media | Upload one image, 201 |
| PATCH | /api/v1/tours/{tourId}/media/{mediaId} | Change or clear caption |
| PATCH | /api/v1/tours/{tourId}/media/{mediaId}/cover | Select cover image |
| PUT | /api/v1/tours/{tourId}/media/order | Set image order |
| DELETE | /api/v1/tours/{tourId}/media/{mediaId} | Delete image, 204 |
| GET | /api/v1/tours/{tourId}/required-items | Required/personal items checklist |
| GET | /api/v1/tours/{tourId}/equipment | Package contents and individual rental prices |

The existing tour list now accepts optional `query` (case-insensitive literal title
substring), `difficulty`, `durationDays` (positive), and `status` filters. Customers
send `status=PUBLISHED`. Filters apply before pagination; omitted filters retain the
management list behavior. There is no separate catalog endpoint or new role logic.
Schedule lists accept `upcomingOnly=true` to return future SCHEDULED departures in
start-time/id order. Omitting it preserves the original list behavior.

The two equipment reads use the tables/columns already added by migration 007.
An unconfigured package returns `{ "equipmentPackage": null, "items": [] }`.
Package rental price is per participant per tour; individual price is per item per
tour. NULL price means not configured/not rentable; zero is a valid configured
price. The old schedule equipment fee is not added on top of the structured
package price. These APIs provide configuration, not inventory or reservation.
They keep the existing authenticated access behavior and 404 for a missing tour.

The frontend integration contract is documented in the sibling frontend project:
`docs/TOUR_API_CONTRACT.md`. Customer preparation stops at review, with no booking
or payment write, and hands IDs and traveler/rental choices to the Booking owner.

Lists are sorted by id ascending. Page is zero based; size is 1..100. GET list
responses contain summary fields; GET by ID and successful POST/PUT responses
contain detail fields. PUT replaces editable content fields, so omitted optional
content fields become null. Tour status is optional on PUT: omitted or null keeps
the current status; a supplied value must be a valid `TourStatus` and satisfy the
existing database rules. Publishing requires a reviewer, review date and cancellation
policy version. Enum values use
Java names, e.g. EASY, MODERATE, HARD, EXPERT, DRAFT and SCHEDULED.

### Tour requests

`POST /api/v1/tours` uses `CreateTourRequest` and always creates a DRAFT tour. Its
body contains the fields below; `status` is not accepted as a create field.

```json
{
  "title": "Ta Nang - Phan Dung",
  "description": "Trekking 2 ngay",
  "difficulty": "MODERATE",
  "durationDays": 2,
  "requiredExperience": "Co the luc tot",
  "includedServices": "Huong dan vien",
  "excludedServices": "Chi phi ca nhan",
  "routeFileUrl": null
}
```

Only title is required. durationDays, if supplied, must be positive.

`PUT /api/v1/tours/{tourId}` uses `UpdateTourRequest`. It accepts the same content
fields and may include a status, for example `"status": "PUBLISHED"`.

Publishing requires existing `reviewedBy`, `reviewedAt` and
`cancellationPolicyVersionId` metadata. Missing values return 409
`TOUR_CANNOT_BE_PUBLISHED`, with one `fieldErrors` entry per missing value.
These fields are backend managed and are not accepted in this update DTO.
The review/policy assignment flow must set them before publishing; this refactor
does not add that flow or require new fields such as images to publish.

### Schedule requests

POST uses `CreateScheduleRequest`; PUT uses `UpdateScheduleRequest`. Both accept
the editable fields below.

```json
{
  "startsAt": "2027-01-10T07:00:00+07:00",
  "endsAt": "2027-01-12T17:00:00+07:00",
  "capacity": 10,
  "basePriceVnd": 1500000,
  "equipmentFeeVnd": 200000,
  "depositPercent": 30.00,
  "balanceDueAt": "2027-01-09T17:00:00+07:00",
  "meetingPoint": "Da Lat",
  "contactPhone": "0900000000"
}
```

All fields except meetingPoint and contactPhone are required. Dates include an
offset. endsAt must be after startsAt; balanceDueAt must be on or before startsAt.
Prices are integer VND in 0..1,000,000,000,000; depositPercent is >0 and <=100 with
at most two fractional digits. tourId comes from the path and cannot be reassigned.
Seat counters, schedule status, completion and review metadata are server owned.
Tour summary responses contain `id`, `title`, `difficulty`, `durationDays` and
`status`. Schedule summary responses contain `id`, start/end time, capacity, held
and confirmed seats, base/equipment prices and status. Detail responses retain
the full fields previously returned by the API.

### Tour activities

Activities belong to the reusable Tour and are grouped by `dayNumber`. Each row
stores its day number, order, optional start time, title, description and optional
location. `GET /api/v1/tours/{tourId}/activities` returns them ordered by day,
then order. Create or update one activity at a time; the existing `tour_activities`
table already supports these fields, so no database change is needed for this API.

### Tour images

Cloudinary stores image files. Database rows store `url` and the private
`cloudinary_public_id`; the latter is never returned to clients. Tour details
already return `media`, ordered by `displayOrder` then id, and tour summaries
include `coverImageUrl`. Setup and Postman examples: [Tour images](../../../../../../../../docs/TOUR_IMAGES.md).

## Boundaries

- Missing resources and schedules requested through another Tour return 404.
- Tour deletion requires DRAFT and no schedules; otherwise 409. Uploaded Cloudinary
  images must be deleted through the image API first (`TOUR_HAS_MEDIA`), so the
  database cascade cannot leave their files on Cloudinary. No schedule cascade.
- Schedule update/delete requires SCHEDULED and zero held/confirmed seats; otherwise 409.
- Database references (e.g. bookings, carts, guide assignments) block deletion with 409.
- Row locks serialize tour update/delete/create-schedule and schedule update/delete.
- Schedule cancellation/refunds, attendance, equipment reservation and role checks
  are separate integration work.
- Existing SecurityConfig requires a valid bearer token for all these routes. Role
  authorization is deferred by agreement: currently any authenticated account can
  call this initial CRUD. Add permission checks before making it available to customers.

The MVC/service tests use mocked repositories and do not exercise the security
filter chain, database foreign keys or transaction locks.

Tour business errors use `TourException` and are translated by
`TourGlobalExceptionHandler`, scoped to the tour controller package. The shared
`ApiError` response and existing business error codes are retained. `fieldErrors`
maps field names to messages for frontend forms; errors on backend managed fields
should be shown as form-level messages. Request validation still uses the shared
core handler. Tour-specific malformed input handling identifies JSON fields with
the wrong type/format (`INVALID_FIELD_VALUE`) and invalid enum values
(`INVALID_ENUM_VALUE`), including the allowed, case-sensitive enum names. Invalid
path/query parameters and missing file parts also identify the offending field.
Malformed JSON without an identifiable field returns 400 `INVALID_REQUEST`.

For example, `"status": "published"` returns 400:

```json
{
  "code": "INVALID_ENUM_VALUE",
  "message": "Invalid request field",
  "fieldErrors": {
    "status": "Must be one of: DRAFT, PENDING, PUBLISHED, REJECTED, ARCHIVED (case-sensitive)"
  }
}
```

Known database constraints return specific 409 codes: `TOUR_CANNOT_BE_PUBLISHED`,
`ACTIVITY_ORDER_CONFLICT`, `INVALID_LOCATION`, `TOUR_COVER_CONFLICT`,
`TOUR_IMAGE_CONFLICT`, `INVALID_CANCELLATION_POLICY` and `INVALID_TOUR_REVIEWER`.
Unrecognized database integrity errors still return 409 `TOUR_DATA_CONFLICT`;
their details are logged on the backend for investigation. Other database errors
return 503 `TOUR_UNAVAILABLE`. SQL and database record details are never returned
in API errors. Database constraints and endpoints are unchanged by this refactor.

The shared `OpenApiContractIntegrationTests` inventory includes the tour,
schedule, activity and image operations. The full test suite
requires a running Docker daemon for its PostgreSQL/Redis Testcontainers.

```powershell
.\mvnw.cmd '-Dtest=TourCrudTests' test
```
