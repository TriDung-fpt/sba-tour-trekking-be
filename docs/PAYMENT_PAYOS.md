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

Register `POST /api/v1/payments/payos/webhook` as the payment channel webhook URL in PayOS. The backend accepts payment state only from a signature-verified webhook, not from the browser return URL.

## Customer API

Create or reuse an active PayOS checkout link:

```http
POST /api/v1/bookings/{bookingId}/payments/payos
Authorization: Bearer <access-token>
Content-Type: application/json

{"purpose":"DEPOSIT"}
```

`purpose` is `DEPOSIT`, `BALANCE`, or `FULL`. The amount is derived from the persisted booking total, deposit percentage, and accepted payments; the client cannot submit an amount. The response contains the checkout URL and payment attempt reference.

The booking authorization boundary verifies ownership and eligibility and locks the booking while a link is created. Repeated requests reuse a still-active attempt of the same purpose.

## Current integration boundary

The webhook stores a verified payment and marks it accepted only when amount, attempt state, and booking outstanding balance match. Late, cancelled-booking, or excess payments are stored as `pending_review` and require reconciliation. Booking status transitions after accepted payment remain owned by the Booking feature and must be connected through its agreed service contract.

Apply migration `013_add_payment_checkout_url.sql` after migration 012. Fresh database setup runs the full migration chain after `schema.sql` bootstrap.


