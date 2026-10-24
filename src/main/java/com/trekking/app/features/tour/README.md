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
| GET | /api/v1/tours?page=0&size=20 | Paginated tours |
| GET | /api/v1/tours/{tourId} | Tour details |
| PUT | /api/v1/tours/{tourId} | Replace editable tour fields |
| DELETE | /api/v1/tours/{tourId} | Delete unused draft, 204 |
| POST | /api/v1/tours/{tourId}/schedules | Create scheduled departure, 201 |
| GET | /api/v1/tours/{tourId}/schedules?page=0&size=20 | Paginated departures |
| GET | /api/v1/tours/{tourId}/schedules/{scheduleId} | Departure details |
| PUT | /api/v1/tours/{tourId}/schedules/{scheduleId} | Replace editable departure fields |
| DELETE | /api/v1/tours/{tourId}/schedules/{scheduleId} | Delete unused departure, 204 |

Lists are sorted by id ascending. Page is zero based; size is 1..100. PUT is a full
replacement of editable fields: omitted optional fields become null. Enum inputs
and outputs use Java names, e.g. EASY, MODERATE, HARD, EXPERT, DRAFT and SCHEDULED.

### Tour request (POST and PUT)

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

### Schedule request (POST and PUT)

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
Seat counters, status, completion and review metadata are server owned.

## Boundaries

- Missing resources and schedules requested through another Tour return 404.
- Tour deletion requires DRAFT and no schedules; otherwise 409. No schedule cascade.
- Schedule update/delete requires SCHEDULED and zero held/confirmed seats; otherwise 409.
- Database references (e.g. bookings, carts, guide assignments) block deletion with 409.
- Row locks serialize tour update/delete/create-schedule and schedule update/delete.
- Publishing, cancellation/refunds, attendance, equipment reservation and role checks
  are separate integration work. CRUD does not change lifecycle statuses.
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

The shared `OpenApiContractIntegrationTests` inventory includes the ten protected
tour and schedule operations (36 operations on 28 paths). The full test suite
requires a running Docker daemon for its PostgreSQL/Redis Testcontainers.

```powershell
.\mvnw.cmd '-Dtest=TourCrudTests' test
```
