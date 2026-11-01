# PayOS payment flow

## Configuration

Set the PayOS channel credentials and frontend URLs in the local `.env` file:

```dotenv
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=http://localhost:3000/payment/result
PAYOS_CANCEL_URL=http://localhost:3000/payment/cancel
PAYOS_API_BASE_URL=https://api-merchant.payos.vn
```

Register `POST /api/v1/payments/payos/webhook` as the payment channel webhook URL in PayOS. The backend accepts payment state only from a signature-verified provider webhook, not from the browser return URL. Providers implement the shared `PaymentGateway` contract for link creation and webhook verification/parsing.

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


