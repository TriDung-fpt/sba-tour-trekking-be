# Cancel & refund workflow

Migration: `V34__refund_workflow.sql`.

## Flow

```
customer cancels booking (+ bank account)
        │  amount = (accepted payments − refunds already paid) × tier % of the booking's cancellation policy
        ▼
   REQUESTED ──reject──▶ REJECTED            (bank details wiped)
        │ approve (finance may lower the amount, never raise it)
        ▼
   APPROVED ──manual-transfer──────────────▶ COMPLETED   (bank_transfer, transaction ref + optional evidence)
        │ payos-payout (explicit click, only if PAYOS_PAYOUT_* configured)
        ▼
  PROCESSING ──payos-sync: success──▶ COMPLETED   (payos)
             ──payos-sync: failed / not found──▶ back to APPROVED (retry or transfer manually)
```

Nothing moves money automatically: there is no scheduler, every payout step is a finance action.
The approver and the recorder (manual transfer / PayOS payout) must be **different admin accounts**
(`FINANCIAL_SEPARATION_REQUIRED`), enforced by `FinancialOperationAuthorizationService`.

## Refund tiers on the cancellation policy

Publishing a cancellation policy version accepts optional `refundTiers`:

```http
POST /api/v1/admin/policies/{policyId}/versions
{"content":"...","effectiveFrom":"2026-12-01T00:00:00+07:00","refundTiers":[
  {"minHoursBeforeDeparture":168,"refundPercent":100},
  {"minHoursBeforeDeparture":72,"refundPercent":50}
]}
```

The first tier whose `minHoursBeforeDeparture` ≤ hours left before departure applies; no match (or after
departure) means 0%. Earlier cancellation may never refund less than later cancellation. A booking uses the
policy version it was booked under. Versions published without tiers (legacy) request the full paid balance and
finance applies the written policy when approving.

## Customer endpoints

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/bookings/{id}/refund-preview` | paid, %, estimated refund, whether a bank account is required |
| POST | `/api/v1/bookings/{id}/cancel` | `{"reason":"...","refundAccount":{"bankBin":"970436","bankName":"Vietcombank","accountNumber":"0123456789","accountHolderName":"NGUYEN VAN A"}}` — `refundAccount` is required when the estimated refund > 0 (`400 REFUND_ACCOUNT_REQUIRED`) |
| GET | `/api/v1/bookings/{id}/refund` | latest refund (204 if none); account number masked `****6789` |

## Finance endpoints (admin)

| Method | Path | Body |
|---|---|---|
| GET | `/api/v1/admin/refunds?status=requested&page=0&size=20` | — |
| GET | `/api/v1/admin/refunds/{id}` | — (decrypted account number / holder name until the refund closes) |
| POST | `/api/v1/admin/refunds/{id}/approve` | `{"approvedAmountVnd":25000,"note":"..."}` (both optional) |
| POST | `/api/v1/admin/refunds/{id}/reject` | `{"note":"..."}` |
| POST | `/api/v1/admin/refunds/{id}/manual-transfer` | `{"transactionReference":"FT123","evidenceUrl":"https://...","note":"..."}` |
| POST | `/api/v1/admin/refunds/{id}/payos-payout` | — (`503 PAYOUT_PROVIDER_UNAVAILABLE` when not configured) |
| POST | `/api/v1/admin/refunds/{id}/payos-sync` | — |

## Bank data encryption

- Account number and holder name are encrypted in the application with **AES-256-GCM** (random 12-byte nonce,
  128-bit tag, column name as associated data) and stored as `v1:base64(nonce|ciphertext|tag)`.
- The key is `REFUND_DATA_ENCRYPTION_KEY` (Base64 of 32 random bytes: `openssl rand -base64 32`). Keep it in the
  deployment secret store only; losing it makes open refund destinations unreadable.
- Bank BIN, bank name and the last 4 digits are stored in plain text for display.
- Ciphertext is deleted when a refund is completed or rejected. Bank details are never logged or written to audit logs.
- Optional PayOS payout channel: `PAYOS_PAYOUT_CLIENT_ID`, `PAYOS_PAYOUT_API_KEY`, `PAYOS_PAYOUT_CHECKSUM_KEY`.

## Customer emails

The customer receives an email (`RefundMailSender`, SMTP, sender `MAIL_FROM`) when a refund becomes
`requested`, `approved`, `rejected` (with the finance note) or `completed`. Emails are sent only after the
transaction commits (`RefundNotificationListener`, `@TransactionalEventListener(AFTER_COMMIT)`), so a rolled-back
action never emails the customer, and a mail failure is logged without undoing the refund. Emails show only the
masked account (`****6789`). PayOS payout failures return the refund to `approved` without emailing the customer.
