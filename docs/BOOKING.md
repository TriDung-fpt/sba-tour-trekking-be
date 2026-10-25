# Booking contract and Payment handoff

## Ownership and pricing

Booking owns travelers, price/policy/rental snapshots, deposit terms, booking status and seat counters.
Tour owns the departure configuration (`basePriceVnd`, `depositPercent`, `balanceDueAt`) and equipment rental prices.
Payment owns payment requests, verified receipts, accepted payment totals and any late/partial payment resolution.

New booking prices are:

```text
totalPriceVnd = schedule.basePriceVnd * travelers.length + group rental total
depositDueVnd = max(1, roundHalfUp(totalPriceVnd * depositPercent / 100))
```

Use decimal arithmetic. 100.49 VND rounds to 100; 100.50 rounds to 101.
The minimum deposit is 1 VND. New bookings with a zero total are rejected with `INVALID_BOOKING_TOTAL`.
`depositDueVnd` is calculated from the booking's stored total and percentage, without another database column.
Changing Tour configuration does not change these stored booking prices or the deposit percentage.

One booking belongs to one authenticated CUSTOMER account and one departure.
Contact is read from that account's current profile; it is not a separately editable contact snapshot.
Each traveler is stored independently in `participants`; travelers need not have accounts or include the owner.
Separate contact details are deferred in issue #5.

## Customer HTTP API

Bearer authentication is required. Responses are direct DTOs, without a `data` envelope.
JSON status names are uppercase (`PENDING_PAYMENT`); database status values are lowercase (`pending_payment`).

### Create: POST /api/v1/bookings

```json
{
  "scheduleId": 42,
  "travelers": [
    { "fullName": "Example Traveler", "email": "traveler@example.invalid", "phone": "+84901234567" }
  ],
  "rental": { "mode": "PACKAGE", "packageId": 7, "quantity": 2 }
}
```

Rental alternatives are `{ "mode": "NONE" }` and
`{ "mode": "ITEMS", "items": [{ "itemId": 11, "quantity": 2 }] }`.
`itemId` identifies a `tour_equipment_items` row, not the equipment catalog ID.
Quantities are for the whole group/trip. Do not send prices, contact, customerId or participantCount.
The backend derives the owner from the session and participantCount from travelers.

Success is HTTP 201, returning the same detail DTO as GET below. The following fields illustrate its money/hold contract:

```json
{
  "id": 123,
  "bookingCode": "BK-example",
  "status": "PENDING_PAYMENT",
  "tourId": 5,
  "tourTitle": "Example tour",
  "scheduleId": 42,
  "participantCount": 1,
  "basePriceVnd": 1000000,
  "rentalFeeVnd": 400000,
  "totalPriceVnd": 1400000,
  "depositPercent": 30.00,
  "depositDueVnd": 420000,
  "holdExpiresAt": "2027-01-01T10:15:00+07:00",
  "balanceDueAt": "2027-01-09T17:00:00+07:00"
}
```

The full detail also includes `contact`, `startsAt`, `endsAt`, `travelers`, `rental` (equipment snapshot),
`createdAt`, `cancelledAt` and `cancellationReason`. Tour title, schedule dates and contact are live reads;
prices, rental description/quantities/prices and cancellation policy are booking snapshots.

The server validates publication, future departure status, ordinary available capacity and rental membership.
Creation atomically inserts `bookings`, inserts `participants` and increments `tour_schedules.held_seats`.
Equipment and policy snapshots are JSONB columns on `bookings`; no rental reservation or cart row is created.

There is no request deduplication or required `Idempotency-Key`. The legacy required database key is filled
with a random backend value. Do not automatically retry creation after an uncertain network failure;
check the customer's booking list first. Concurrent last-seat requests are outside this iteration's guarantees.

### Read and cancel

| Endpoint | Result |
| --- | --- |
| `GET /api/v1/bookings/my?page=0&size=10` | Spring page: `content`, `number`, `size`, `totalPages`, `totalElements`. Summaries include identity, tour/departure, participant count, total, status and hold expiry. Size is 1–100. |
| `GET /api/v1/bookings/{id}` | Full detail DTO, scoped to the authenticated owner. |
| `POST /api/v1/bookings/{id}/cancel` | Body `{ "reason": "Plans changed" }`; nonblank reason, maximum 1000 characters. Returns updated detail. |

Other owners receive HTTP 404. Only pending bookings can be cancelled (otherwise HTTP 409 `BOOKING_NOT_CANCELLABLE`).
Cancellation releases held seats and records who, when and why. If the pending hold already expired,
the cancellation call expires it instead and returns `EXPIRED`. Traveler rows and snapshots remain for history.

Relevant create errors include HTTP 409 `SCHEDULE_FULL`, `SCHEDULE_NOT_BOOKABLE`,
`CANCELLATION_POLICY_UNAVAILABLE`, and HTTP 400 `INVALID_RENTAL_SELECTION`, `INVALID_BOOKING_TOTAL`,
`INVALID_TRAVELER`, `PRICE_OUT_OF_RANGE`. Error DTOs use `code` and `message`; the frontend translates codes.

## Internal interface for Payment

Inject `BookingService`; these are trusted Java service methods, not public HTTP endpoints.
They do not require a customer browser session on callbacks.

```java
BookingPaymentContext getPaymentContext(long bookingId, long customerId);
BookingStatus confirmDeposit(long bookingId, long verifiedPaidTotalVnd);
```

`getPaymentContext` checks the supplied owner and returns `bookingId`, `customerId`, `status`,
`totalPriceVnd`, `depositPercent`, `depositDueVnd`, `holdExpiresAt`, `balanceDueAt`.
Payment must obtain customerId from its authenticated caller, not accept an arbitrary client owner ID.
The result is a read snapshot, not a reservation extension or authorization to confirm.

Payment must verify receipts and persist its accepted receipts, then pass their cumulative accepted VND total
to `confirmDeposit` in the same outer database transaction. Never forward browser success flags or a client amount.
The existing receipt-insert trigger locks `price_locked_at`; Booking flushes and refreshes its locked entity
so it also observes that trigger when Payment and Booking share a persistence context.

| Current state / condition | Returned state / Booking action |
| --- | --- |
| Pending, now >= holdExpiresAt | EXPIRED; release held seats once, even if the supplied total is sufficient. |
| Pending, still in time, total < depositDueVnd | PENDING_PAYMENT; keep the original hold deadline and counters. |
| Pending, still in time, total >= depositDueVnd | CONFIRMED; held -= participantCount, confirmed += participantCount; preserve/set price_locked_at. |
| Already CONFIRMED, EXPIRED, CANCELLED, COMPLETED or NO_SHOW | Return that state; no second counter update and no revival. |

A negative verified total is rejected with `INVALID_PAYMENT_TOTAL`; a missing booking is not found.
Confirmation holds a booking-row write lock and uses the schedule's existing optimistic version.
The caller must commit the transaction before considering the booking transition complete.
Do not roll back an otherwise valid receipt merely because the returned state is EXPIRED or CANCELLED:
Payment must retain and resolve that money through its own late-payment process.

The strict deadline is the time Booking processes confirmation, with no grace window based on a provider timestamp.
Do not create a payment request whose expiry exceeds the booking hold deadline.
There is no automatic restoration of an expired hold. Full payments also satisfy the deposit threshold.
Any repeated callback must still be deduplicated/recorded correctly by Payment; Booking's state guard only
prevents moving seats a second time. No payment, refund, checkout or webhook implementation is included here.

## Expiry and frontend behavior

Holds last 15 minutes from creation. The scheduler scans pending bookings every 60 seconds by default
(`app.booking.expiry-scan-ms`) in batches of 100. Creation also clears overdue holds for its departure.
A read does not itself expire a booking. An elapsed pending hold may remain visible until the next scan;
the frontend refetches at zero and every 30 seconds while pending. Confirmed bookings are never expired by this job.
Overdue balance payments after confirmation need a separate business rule and are not implemented.

Tour selection remains a browser draft until the customer presses Reserve places. Only then does it POST.
After success, the URL includes bookingId so reload opens the server-backed detail under the existing eticket screen.
The customer list/detail/cancel flow uses the Booking API, not the legacy wetrek_booking_v1 mock state.
Payment screens and their existing API wrapper remain unchanged for the Payment owner.

## Deployment and checks

Apply missing migrations in sequence with application writers stopped. Existing volumes do not replay Compose init scripts.
013 adds group rental pricing and rebuilds the generated total while preserving old per-person fee totals.
014 extends the paid snapshot guard to group_rental_fee_vnd. Do not rely on Hibernate ddl-auto=update
to replace a generated-column expression. No new table or entity is introduced for this handoff.

This iteration adds no test cases. Build backend with `./mvnw '-Dmaven.test.skip=true' package`;
on Windows use `mvnw.cmd` with the property quoted. Frontend checks are `npm run lint` and `npm run build`.
Inspect the customer browser create/list/detail/cancel flow against bookings, participants and tour_schedules
in the local database. Confirmation remains an internal contract until the Payment owner integrates verified receipts.
