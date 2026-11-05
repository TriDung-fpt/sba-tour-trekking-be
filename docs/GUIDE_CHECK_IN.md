# Guide participant check-in

An assigned active guide can record one check-in per participant on a confirmed booking:

`POST /api/v1/schedules/{scheduleId}/bookings/{bookingId}/participants/{participantId}/check-in`

This replaces the earlier in-progress `/api/v1/bookings/{bookingId}/participants/{participantId}/check-in` route; clients must supply the schedule ID in the new route.

The authenticated guide sends `{ "note": "Optional note" }` (maximum 1000 characters). The response contains `participantId`, `bookingId`, `checkedInAt`, `checkedInBy`, and `note`. Repeating the request returns the original check-in without changing it. An unassigned guide, a booking outside the schedule, or a participant outside the booking receives 404; a booking that is not confirmed receives 409. The tour service checks guide assignment and calls the booking service to update the participant within the same transaction.

## End-of-trip check-out

An assigned guide records the end-of-trip count for a checked-in participant with
`POST /api/v1/schedules/{scheduleId}/bookings/{bookingId}/participants/{participantId}/check-out`.
Send `{ "note": "Optional note" }` (maximum 1000 characters). The response contains `participantId`,
`bookingId`, `checkedOutAt`, `checkedOutBy`, and `note`. A repeated request returns the original record.
The guide, booking and participant must match the schedule; the participant must already be checked in and
the booking must be confirmed or completed. Other booking states or missing check-in return 409.

Migration 024 creates a tour-owned checkout record, separate from the participant entity. Checkout is
an end-of-trip operation, but the API cannot enforce the actual trip end until a trip completion operation
exists. Guide assignment management, a schedule manifest and recording absences are not yet provided.
