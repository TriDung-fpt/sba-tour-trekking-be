# Tour statistics

`GET /api/v1/admin/schedules/overview` requires `SCHEDULE_MANAGE` permission.
It returns a paginated list of non-archived tours with `tourId`, `title`, `status`,
and `scheduleCount`. The count includes departures on every date and in every
schedule status, including cancelled departures; tours without departures have a
count of zero. Optional `query` searches tour titles case-insensitively; `page`
is zero-based and `size` must be between 1 and 100.

`GET /api/v1/tours/{tourId}/statistics` requires an administrator with `TOUR_READ` permission.
It returns `scheduleCount` for all schedules and an `equipment` array containing `equipmentId`,
`name`, and `quantity`. Equipment quantities sum confirmed, issued, return-required, and returned
reservations across the tour's schedules. Held and released reservations are excluded. Quantities
are cumulative across bookings, not a measure of simultaneous inventory usage.

`GET /api/v1/tours/schedules?start=...&end=...` is the administrator calendar API.
Each departure now also includes `scheduleCount` (all departures for that tour,
including departures outside the requested range and in every schedule status),
`capacity`, `confirmedSeats`, and `heldSeats`. Confirmed seats represent passengers
with confirmed bookings, while held seats are temporary reservations and may include
expired holds until the expiration job releases them. The query counts schedules in one
batch for the tours shown in the range.

`GET /api/v1/bookings/{bookingId}` includes `equipmentReservation`, using the same
response as `GET /api/v1/bookings/{bookingId}/equipment/reservation`. It is `null`
when the booking has no equipment reservation. Items include the equipment ID, name,
quantity, issued quantity, and returned quantity; the reservation status indicates
whether it is held, confirmed, issued, released, return-required, or returned.
Equipment is selected for a booking, not attributed to individual passengers.
The existing booking authorization continues to apply to the entire response.
Equipment names reflect the current catalog name; deleted catalog rows are retained.
