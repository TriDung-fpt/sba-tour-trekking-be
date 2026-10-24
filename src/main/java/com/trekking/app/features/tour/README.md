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

Lists are sorted by id ascending. Page is zero based; size is 1..100. GET list
responses contain summary fields; GET by ID and successful POST/PUT responses
contain detail fields. PUT replaces editable content fields, so omitted optional
content fields become null. Tour status is optional on PUT: omitted or null keeps
the current status; a supplied value sets any valid `TourStatus`. Enum values use
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
`ApiError` response and existing business error codes are retained. Untranslated
database integrity errors return 409 (`TOUR_DATA_CONFLICT`); other database errors
return 503 (`TOUR_UNAVAILABLE`) without database details. Shared request validation
and malformed JSON handling continue to use the core exception handler.

The shared `OpenApiContractIntegrationTests` inventory includes the tour,
schedule, activity and image operations. The full test suite
requires a running Docker daemon for its PostgreSQL/Redis Testcontainers.

```powershell
.\mvnw.cmd '-Dtest=TourCrudTests' test
```
