# PayOS payment flow

## Configuration

Set the PayOS channel credentials and frontend URLs in the local `.env` file:

```dotenv
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=http://localhost:5173/payment/result
PAYOS_CANCEL_URL=http://localhost:5173/payment/cancel
PAYOS_API_BASE_URL=https://api-merchant.payos.vn
```

Register `POST /api/v1/payments/payos/webhook` as the payment channel webhook URL in PayOS. The backend accepts payment state only from a signature-verified provider webhook, not from the browser return URL. Providers implement the shared `PaymentGateway` contract for link creation and webhook verification/parsing.

The local frontend runs on Vite port `5173`, so the return and cancel URLs above must use that port. The result screen waits for server-side payment confirmation; cancelling checkout returns the customer to the active booking's deposit page while the 15-minute hold remains, or to the bookings list after it expires.

## Customer API

PayOS URL confirmation sends a signed sample (`orderCode=123`, `amount=3000`,
`reference=TF230204212323`). After signature verification, the backend acknowledges
this sample with HTTP 200 without updating payments. Other unknown order codes
still return 404. Webhook verification only requires `PAYOS_CHECKSUM_KEY`;
checkout creation requires all credentials and return/cancel URLs.

When changing `.env` with Docker Compose, recreate the backend container using
`docker compose up -d --force-recreate backend`. After code changes, use
`docker compose up -d --build backend`.

Create or reuse an active checkout link. The customer selects the provider in the request; currently PayOS has an adapter configured:

```http
POST /api/v1/bookings/{bookingId}/payments
Authorization: Bearer <access-token>
Content-Type: application/json

{"provider":"PAYOS","purpose":"DEPOSIT"}
```

`provider` identifies the selected gateway and `purpose` is `DEPOSIT`, `BALANCE`, or `FULL`. The amount is derived from the persisted booking total, deposit percentage, and accepted payments; the client cannot submit an amount. The response contains the chosen provider, checkout URL, and payment attempt reference. A provider without a registered adapter returns `PAYMENT_PROVIDER_UNAVAILABLE`.

The booking authorization boundary verifies ownership and eligibility and locks the booking while a link is created. Repeated requests reuse a still-active attempt of the same purpose.

## Current integration boundary

The webhook stores a verified payment and marks it accepted only when amount, attempt state, and booking outstanding balance match. Late, cancelled-booking, or excess payments are stored as `pending_review` and require reconciliation. Booking status transitions after accepted payment remain owned by the Booking feature and must be connected through its agreed service contract.

Apply migration `013_add_payment_checkout_url.sql` after migration 012. Fresh database setup runs the full migration chain after `schema.sql` bootstrap.

## Refund workflow

Apply migration 023 after 022. A booking snapshots the active refund tiers when it is created, so later policy changes only affect new bookings. Configure tiers with `POLICY_MANAGE`:

```http
POST /api/v1/admin/refund-rules
Authorization: Bearer <access-token>
Content-Type: application/json

{"effectiveFrom":"2027-01-01T00:00:00Z","rules":[
  {"minimumHoursBeforeDeparture":168,"refundPercent":100},
  {"minimumHoursBeforeDeparture":72,"refundPercent":50},
  {"minimumHoursBeforeDeparture":0,"refundPercent":0}
]}
```

When cancelling a booking, a customer can include a payout destination to request a refund in the same operation. The backend calculates the amount from the booking's saved tiers and accepted payments; the client does not submit an amount:

```json
{"reason":"Customer cancellation","refundRequest":{
  "bankBin":"970418","accountNumber":"0123456789","accountHolderName":"NGUYEN VAN A"
}}
```

Refund destination fields are AES-GCM encrypted at rest. Set a private 32-byte Base64 key in `REFUND_DATA_ENCRYPTION_KEY` (generate with `openssl rand -base64 32`) and the PayOS payout channel credentials in `PAYOS_PAYOUT_CLIENT_ID`, `PAYOS_PAYOUT_API_KEY`, and `PAYOS_PAYOUT_CHECKSUM_KEY`. Keep these values only in the local/deployment secret store. Refund requests can be listed by staff with `FINANCE_READ`; full destination details and approve/reject actions require `REFUND_APPROVE`. Approval sends the payout to PayOS. A scheduled reconciliation checks in-flight payouts every minute and records terminal success/failure; terminal emails omit bank details.

Customer status is available at `GET /api/v1/bookings/{bookingId}/refund`. Finance endpoints are `GET /api/v1/admin/refunds`, `GET /api/v1/admin/refunds/{refundId}`, and `POST /api/v1/admin/refunds/{refundId}/approve` or `/reject` (reject body: `{"note":"..."}`). PayOS name/account verification is not currently integrated, so staff must verify the destination manually before approving. The full destination is removed from the database after PayOS reports a terminal result.


