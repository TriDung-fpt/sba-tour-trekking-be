# Tour statistics

`GET /api/v1/tours/{tourId}/statistics` requires an administrator with `TOUR_READ` permission.
It returns `scheduleCount` for all schedules and an `equipment` array containing `equipmentId`,
`name`, and `quantity`. Equipment quantities sum confirmed, issued, return-required, and returned
reservations across the tour's schedules. Held and released reservations are excluded. Quantities
are cumulative across bookings, not a measure of simultaneous inventory usage.
