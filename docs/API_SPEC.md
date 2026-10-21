# WeTrek tour API contract — PA-01/02/03/04/10/11 and CU-01/02

## Partner operations MVP (PO-01)

This section defines partner booking, departure, roster, check-in, cancellation, and statement reads. Customer booking creation and payment collection are defined below. All partner endpoints require an active owner/manager membership in an approved company, except check-in, which also permits a guide assigned to that departure. A company ID supplied by the client is never trusted. PII is limited to what is needed to operate the trip.

| Method and path | Access | Purpose |
| --- | --- | --- |
| GET `/partner/bookings?status&dateFrom&dateTo&query&page&size` | Company owner/manager | List that company's bookings, optionally filter by status, departure date range, or booking code/customer name. |
| GET `/partner/bookings/{bookingId}` | Company owner/manager | Read booking, departure, minimal customer contact, participant list, payment totals, and refund request summaries. |
| GET `/partner/departures?from&to&status&page&size` | Company owner/manager | Read company departures for day-to-day operations without fetching each tour separately. |
| GET `/partner/statements?page&size` | Company owner/manager | Read settlement statements and recorded transfers; partner access is read-only. |
| GET `/partner/guides` | Company owner/manager | List active guide accounts for the caller's approved companies, for assignment controls. |
| GET `/partner/tours/{tourId}/schedules/{scheduleId}/roster?page&size` | Owning company member | Read confirmed/completed/no-show booking participants; expired and pending holds are excluded. |
| POST `/partner/bookings/{bookingId}/participants/{participantId}/check-in` | Active company member or assigned guide | Check in one participant; request `{note?}`. Repeating check-in is idempotent and returns the existing check-in. |
| DELETE `/partner/bookings/{bookingId}/participants/{participantId}/check-in` | Company owner/manager | Undo a mistaken check-in before the schedule ends; request `{reason}` and record an audit event. |
| POST `/partner/bookings/{bookingId}/cancel` | Company owner/manager | Request cancellation of a booking with `{reason}`. Server validates lifecycle and records actor/reason. It does not create a refund or claim a refund is complete. |

Booking list response: `{items:[{id,bookingCode,status,createdAt,departure:{scheduleId,tourId,tourTitle,startsAt,endsAt},participantCount,totalPriceVnd,receivedVnd,pendingReviewVnd,refundedVnd}],page,size,totalItems,totalPages}`. Statuses are `pending_payment`, `confirmed`, `expired`, `cancelled`, `completed`, `no_show`. Pending holds past `holdExpiresAt` are shown as `expired` in reads and do not count as available seats; background expiry owns the persisted transition.

Booking detail response: `{id,bookingCode,status,createdAt,departure:{scheduleId,tourId,tourTitle,startsAt,endsAt,meetingPoint,contactPhone},customer:{displayName,email,phone},participants:[{id,fullName,email?,phone?,checkedInAt?,checkInNote?}],money:{totalPriceVnd,receivedVnd,pendingReviewVnd,refundedVnd,depositPercent,balanceDueAt},refundRequests:[{id,requestedAmountVnd,approvedAmountVnd?,status,reason,decisionNote?,createdAt}]}`. Partner detail may show participant contact data only for confirmed bookings and members authorized for trip operations. `receivedVnd` includes accepted payments; funds with `pending_review` allocation are reported separately as `pendingReviewVnd`. Do not expose transaction references, evidence URLs, account IDs, payment tokens, gateway payloads, or private cancellation policy internals.

Departures response: `{items:[{scheduleId,tourId,tourTitle,startsAt,endsAt,status,meetingPoint,contactPhone,capacity,heldSeats,confirmedSeats,availableSeats,participantCount,guideNames}],page,size,totalItems,totalPages}`. Include the caller's approved-company departures with status `scheduled`, `ongoing`, or `completed` by default, filtered by ISO instant `from` inclusive and `to` exclusive; default window is now minus one day through 90 days later. `status=cancelled` can explicitly request cancelled departures. Statements response: `{items:[{id,periodStart,periodEnd,status,payableVnd,transferredVnd,remainingVnd,transfers:[{amountVnd,transferredAt}],note}],page,size,totalItems,totalPages}`; statuses are `draft`, `approved`, `paying`, `paid`, `failed`. Do not expose bank references or evidence URLs in partner DTOs. Statement and transfer rows are read-only to partner users; admins can generate, approve, and record evidence of already-executed transfers as documented below. The platform never initiates a bank transfer.

Guide options response: `{items:[{id,companyId,displayName,active}]}`. Only active guides whose account is active and whose company is approved are returned.

Cancellation is allowed only for `pending_payment` or `confirmed` bookings before departure starts, when there is no active payment hold ambiguity; a confirmed cancellation updates the booking state but does not itself execute a refund. An expired hold is not cancellable. An authorized manager or assigned guide can complete an ended departure after active payment holds are resolved. At that point each remaining `confirmed` booking becomes `completed` if any participant has a check-in record, otherwise `no_show`; each booking transition and the departure completion are audited. Customers may request a refund for their cancelled or expired booking; admins decide the amount and record a refund only after actual external execution with evidence. Partners see request status and decision note only. Only a verified gateway webhook or an authorized admin manual verification may create a received payment. Refund transactions are recorded only after the actual transfer is verified and evidenced.

Pagination is zero-based, defaults to `page=0&size=20`, and `size` is limited to 1–100. Dates are ISO-8601 instants. List search is case-insensitive and bounded; invalid status/date windows return 400. Missing or non-owned booking/participant IDs return 404. Invalid transitions return 409. Mutations run transactionally, lock the booking/participant row where competing updates could conflict, and write an audit record. Check-in is idempotent; cancel requires a reason and repeated cancellation returns the current state without duplicating audit side effects.

## Finance operations (FO-01)

All `/admin/**` finance routes require `ADMIN`. Every mutation requires `Idempotency-Key` (1–100 characters). Keys and normalized request fingerprints are stored in immutable audit rows; repeated same-key/same-payload requests return the existing result, while reuse with a different payload returns 409. Writes lock the relevant booking, refund request, or statement and are audited. Evidence must be an absolute HTTP(S) URL. These endpoints record verification and evidence only; none starts a gateway refund or bank transfer.

### Payment verification

| Method and path | Access | Purpose |
| --- | --- | --- |
| GET `/admin/payments/pending-review?page&size` | Admin | List real ledger receipts not yet allocated to booking revenue. |
| POST `/admin/payments/{paymentId}/verify` | Admin + idempotency | Reconcile an existing pending-review receipt. |
| POST `/admin/bookings/{bookingId}/manual-payments` | Admin + idempotency | Record a bank payment already externally verified by an administrator. |

Pending-review response: `{items:[{id,bookingId,bookingCode,provider,transactionReference,amountVnd,receivedAt,attempt:{purpose,requestedAmountVnd}|null}],page,size,totalItems,totalPages}`. Provider is `payos`, `vnpay`, or `manual`. The admin verification request is `{evidenceUrl,reason}`; response is `{paymentId,allocationStatus,bookingStatus,receivedVnd,pendingReviewVnd}`. Verification only accepts the exact currently due amount when the booking lifecycle, hold, departure, and held-seat counters allow safe confirmation. Otherwise the request returns 409 and funds remain `pending_review` so they can be refunded with an evidence-backed refund record.

Manual payment request: `{purpose:"deposit"|"balance"|"full",amountVnd,transactionReference,receivedAt,evidenceUrl,reason}`. The admin is attesting that the referenced payment already happened; the server never sends a payment request. If the amount exactly matches the current due amount and the booking can safely transition, the ledger row is accepted and a pending hold is converted to confirmed seats. Otherwise it is stored as a real `manual` receipt with `pending_review`. Reusing a manual bank reference returns 409. The `Idempotency-Key` is scoped to the operation and request contents.

### Refund requests and recording

| Method and path | Access | Purpose |
| --- | --- | --- |
| POST `/bookings/{bookingId}/refund-requests` | Booking owner + idempotency | Request a refund for an owned cancelled or expired booking. |
| GET `/admin/refund-requests?status&page&size` | Admin | Review requests and their recorded refund history. |
| POST `/admin/refund-requests/{refundRequestId}/decision` | Admin + idempotency | Approve an amount or reject with a reason. |
| POST `/admin/refund-requests/{refundRequestId}/record-refund` | Admin + idempotency | Record one already-executed refund transaction with evidence. |

Customer request body is `{reason}`. The server calculates the currently refundable ceiling from accepted receipt rows less completed refunds and other reserved requests; pending-review receipts are excluded. For bookings created after tour refund rules are configured, the ceiling also follows the rules snapshot saved on the booking. The request is rejected with `409 REFUND_AFTER_SETTLEMENT` if the booking already has a sale statement line. Legacy bookings without structured rules retain the existing manual-review ceiling; the admin must still execute and record any refund after actual external transfer.

Admin list response: `{items:[{id,bookingId,bookingCode,companyId,companyName,requestedAmountVnd,approvedAmountVnd,status,reason,decisionNote,createdAt,refundedVnd,refunds:[{id,amountVnd,channel,transactionReference,refundedAt,evidenceUrl}]}],page,size,totalItems,totalPages}`. Statuses are `requested`, `approved`, `processing`, `completed`, `failed`, `rejected`. Decision request: `{decision:"approve"|"reject",approvedAmountVnd?,note}`. Reject requires a nonblank note. Approval requires an amount no greater than the request and no greater than accepted (reconciled) payments after existing refunds and reservations; pending-review receipts are never refundable. Approval records an authorization only; it does not execute a refund. If a booking already has a `sale` line in a statement, approval returns `409 REFUND_AFTER_SETTLEMENT`; post-settlement refund adjustments are not supported, so the admin must resolve the settlement accounting workflow before approval.

Record-refund request: `{amountVnd,channel:"payos"|"vnpay"|"bank_transfer",transactionReference,refundedAt,originalPaymentId?,evidenceUrl}`. It must describe an already completed external transfer, be within the approved outstanding amount, and use a unique idempotency key and channel/reference. Partial refunds remain `processing`; the request becomes `completed` only after recorded refunds reach the approved amount. Response: `{refundRequestId,status,requestedAmountVnd,approvedAmountVnd,refundedVnd}`. A refund on a post-statement booking is not automatically represented as a statement adjustment; see schema limits below.

### Partner statement generation and actual transfer recording

| Method and path | Access | Purpose |
| --- | --- | --- |
| GET `/admin/finance/companies?periodStart&periodEnd&page&size` | Admin | Choose an approved company with eligible settlement lines in the requested period. |
| GET `/admin/statements?status&companyId&page&size` | Admin | Read statement totals, lines, and transfer evidence. |
| POST `/admin/statements` | Admin + idempotency | Create a draft from eligible settled bookings. |
| POST `/admin/statements/{statementId}/approve` | Different admin + idempotency | Approve a positive draft for payout outside the platform. |
| POST `/admin/statements/{statementId}/transfers` | Admin + idempotency | Record a transfer that has already been executed. |

Company selector response: `{items:[{id,name,eligibleBookingCount}],page,size,totalItems,totalPages}`. A booking is eligible only when its booking and schedule are `completed` or `no_show`/`completed` respectively, the actual schedule completion is at least 72 hours old and falls within inclusive `periodStart`/`periodEnd` in `Asia/Ho_Chi_Minh`, accepted receipts cover the snapshot total, there are no pending-review receipts or unresolved `requested` refund requests, and it has no prior initial sale line. Draft generation rechecks these conditions at its cutoff; it subtracts actual refunds, applies the booking's saved platform-fee percentage to retained revenue, and holds approved but unpaid refund amounts from the partner balance. It does not duplicate a booking or guess missing payment rows.

Admin statement response: `{items:[{id,companyId,companyName,periodStart,periodEnd,cutoffAt,status,payableVnd,transferredVnd,remainingVnd,note,items:[{bookingId,bookingCode,kind,retainedRevenueVnd,platformFeeVnd,heldAmountVnd,payableVnd}],transfers:[{amountVnd,bankReference,transferredAt,evidenceUrl}]}],page,size,totalItems,totalPages}`. Item kinds are `sale` and `adjustment`; this generator currently creates `sale` lines only and skips already-settled initial sale lines. Create request: `{companyId,periodStart,periodEnd,note?}`. The company/period pair is unique and repeated creation with a new key returns 409. Approval request is `{note?}`; the preparer cannot approve their own statement. Transfers request: `{amountVnd,bankReference,transferredAt,evidenceUrl}`. The server requires status `approved` or `paying`, prevents recording more than the remaining payable amount, and sets status to `paying` for partial transfer or `paid` when fully transferred. Status never becomes `paid` from approval alone. The platform does not initiate or schedule a bank transfer.

Existing schema limitation: statement items have no direct refund-transaction allocation, and their nonnegative `held_amount_vnd` cannot represent a later release of a hold. New statement generation therefore skips bookings that already have a `sale` item, and the refund decision endpoint blocks approval after that sale line exists. This prevents an untracked refund from overstating partner balance. Post-settlement refund adjustments require a future schema/API decision. No migration is included in this scope.

## Customer booking creation (BK-01)

| Method and path | Access | Purpose |
| --- | --- | --- |
| POST `/bookings` | Signed-in customer + `Idempotency-Key` header | Atomically hold seats, snapshot price and policy, create booking and participants. |

Create request: `{scheduleId,participantCount,includesEquipment,participants:[{fullName,email?,phone?,accountId?}]}`. `participantCount` must equal the participant array size (1–20). The server locks the schedule row, validates that the tour is published, company approved, departure scheduled and future, and capacity remains; calculates price/deposit from schedule values; snapshots the selected cancellation policy and current company agreement; writes an idempotent booking and participants; increments `held_seats`; sets `status=pending_payment` and `holdExpiresAt=now+15 minutes`. Idempotency is scoped to the authenticated account: a repeated key with the same normalized request returns the original booking, while reuse with a different request returns 409. Success is `201` with booking detail `{id,bookingCode,status,holdExpiresAt,participantCount,totalPriceVnd,depositDueVnd,balanceDueAt,participants:[{id,fullName,email?,phone?}]}`. Do not accept price, deposit, company, customer, booking status, policy snapshot, hold duration or received payment from the client.

This endpoint creates a seat hold. A payment attempt is not a payment. The PayOS checkout endpoints below are available, but return `503 PAYMENT_PROVIDER_NOT_CONFIGURED` until merchant credentials and return/cancel URLs are configured. Only a signature-verified PayOS webhook or an authorized admin's evidence-backed manual payment record may create a received payment; safe booking confirmation still requires exact amount and valid booking/seat state. A scheduled expiry processor releases expired holds transactionally and idempotently. Refund processing remains admin/provider controlled; partners only see refund status as described above.

### PayOS checkout and payment status

| Method and path | Access | Purpose |
| --- | --- | --- |
| POST `/bookings/{bookingId}/payment-links` | Booking owner | Create a PayOS checkout link for a due deposit, full payment, or confirmed-booking balance. |
| GET `/bookings/{bookingId}/payment-status` | Booking owner | Read the current booking payment totals and attempt history. |
| POST `/payments/payos/webhook` | Public provider callback | Verify PayOS signature and idempotently record successful payments. |

Create-link request: `{purpose:"deposit"|"full"|"balance"}`. A pending booking accepts `deposit` or `full`; a confirmed booking accepts `balance`. Amounts and expiry are derived from the booking snapshot and current accepted ledger rows. Response: `{enabled,attemptId,purpose,amountVnd,status,checkoutUrl,qrCode,expiresAt}`. `qrCode` is the raw PayOS EMVCo/VietQR payload string. Return and cancel URLs include `paymentReturn=success|cancelled` and `bookingId`; redirects are not proof of payment. A provider creation failure returns a `failed` attempt without a checkout URL; an already active attempt of the same purpose returns 409. Status response: `{paymentEnabled,bookingStatus,totalPriceVnd,receivedVnd,pendingReviewVnd,refundedVnd,amountDueVnd,attempts:[{id,purpose,amountVnd,status,expiresAt}]}`. `receivedVnd` includes accepted payments; `pendingReviewVnd` reports a real but unreconciled amount. Pending attempts are not funds received.

Configure `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, and `PAYOS_CANCEL_URL` through the backend environment. The SDK verifies the signed webhook before ledger changes. A valid success with an unexpected amount or a booking that can no longer be confirmed is recorded for review rather than confirming seats. Duplicate notices are idempotent. No credentials are committed in the repository. Admins can reconcile pending receipts, record evidence of actual external refunds, and record evidence of already-executed bank transfers as defined in FO-01; the platform does not initiate those money movements.


This is the shared API contract for tour discovery and partner operations. Backend implementation is scoped to `features/tour/**` for tour authoring/departures and `features/booking/**` for customer booking and partner booking operations. Partner statement reads are in `features/statement/**`. Payment collection supports PayOS checkout and signed webhook verification when configured; refunds and partner payouts are still externally executed and only recorded after evidence is provided.

### Payment and settlement implementation boundary

The current backend can create an idempotent 15-minute seat hold, create PayOS checkout links when configured, verify PayOS webhook signatures, reconcile an existing payment with admin evidence, record an already-received manual payment, review and record completed refunds, generate and approve draft statements, and record evidence of transfers already executed outside the platform. It does not initiate refunds or bank transfers. Live PayOS collection requires merchant credentials and a reachable webhook URL configured in PayOS. Never infer payment/transfer success from a booking, payment attempt, provider redirect, partner action, or front-end result. Keep partner statement and booking financial surfaces read-only.

## Partner application API (PA-APP-01)

Public registration collects business/contact details and the required safety commitment. It does not accept a password or grant partner permissions. A one-time email link lets the applicant verify their email and set a password. The applicant signs in with the customer role while the company is pending. Only an admin approval grants `company_manager`; partner operations also require an approved company.

If an unverified account belongs to a pending partner application, `POST /auth/login` returns `403 PARTNER_ACTIVATION_REQUIRED` regardless of the submitted password. The frontend uses this response to redirect directly to the application notice, where the applicant can resend the activation link. Other login failures remain generic.

All endpoints use the `/api/v1` prefix. Public state-changing partner application endpoints use the same `GET /auth/csrf` cookie/header handshake as auth registration. Success responses are direct JSON.

| Method and path | Access | Contract |
| --- | --- | --- |
| POST `/partner-applications` | Public + CSRF | Request `{email,contactName,phone,businessName,taxCode,businessType?,foundedYear?,address?,description?,tourRegions,safetyRulesAccepted:true}`; password is not accepted. Creates an active customer account with a cryptographically random initial password that is never disclosed, a pending company, and an owner membership. The account receives only the `customer` role. Sends a one-time 24-hour activation link; returns 202. |
| POST `/partner-applications/resend-activation` | Public + CSRF | Request `{email}`. Returns a generic 202 response to avoid account discovery. |
| POST `/partner-applications/activate` | Public + CSRF | Request `{token,password}`. Consumes the link once, verifies email and replaces the random initial password with the applicant's chosen password. The applicant can then sign in with customer access while the company is pending. |
| GET `/partner-applications/me` | Signed in | Returns `{status,companyId,businessName,decisionReason}`; `status` is `none`, `pending`, `approved`, or `rejected`. |
| GET `/admin/partner-applications` | Admin | Lists pending submissions for review. |
| POST `/admin/partner-applications/{companyId}/approve` | Admin | Approves the company and grants `company_manager`; this is the only point at which partner role is granted. |
| POST `/admin/partner-applications/{companyId}/reject` | Admin | Request `{reason}`; records the decision and notifies the applicant. The applicant retains customer access and receives no partner role. |

Schema changes are in `src/main/resources/db/migration/V1__partner_applications.sql` and are applied by Flyway at backend startup. Do not edit the baseline `schema/schema.sql` for an existing database or manually run Flyway-managed migrations.

## Conventions and safeguards

- Base path `/api/v1`; success responses are direct JSON, not wrapped in `{data}`. IDs match the current BIGINT schema and serialize as JSON numbers. Treat them as opaque.
- Use record request/response DTOs under `features/tour/dto/request/` and `dto/response/`; controllers depend on a service interface, with implementation under `service/impl/`. Do not expose entities or repositories through controllers.
- Timestamps use ISO 8601 with timezone. Prices are integer VND. The server is authoritative for prices and available seats.
- Bound pagination, validate enums/ranges/date windows, return safe errors, and avoid revealing unpublished tour data, reviewer notes, participant data, or private operational contacts in public responses.
- Every partner/admin operation must verify caller role and resource ownership on the server. For a company-scoped request, never trust a company ID without checking active membership. Roster exports must apply the same authorization and row scope as roster reads.
- Route uploads/URLs must be constrained by allowed format and size. Never execute uploaded content or accept arbitrary HTML. Do not add equipment stock tracking.
- Booking creation and PayOS payment verification are defined by BK-01 above. FO-01 defines manual reconciliation, refund request/recording, statement creation/approval, and evidence-backed transfer recording. No endpoint here initiates refunds or partner transfers.

## CU-01: public search, filters and sort

`GET /tours?query&destinationId&difficulty&dateFrom&dateTo&minPrice&maxPrice&minRating&equipmentPackageId&sort&page&size`

Public. Existing implementation already supports `query`, `destinationId`, `difficulty`, `minPrice`, `maxPrice`, `page`, and `size`. Text searches title/description. Difficulty accepts exact lowercase `easy`, `moderate`, `hard`, or `expert`. Price is nonnegative VND and currently filters the lowest upcoming schedule base price. `page` is zero-based (default 0, max 10,000); `size` defaults to 20 and is bounded to 1–100. Invalid values and `minPrice > maxPrice` return 400.

Remaining CU-01 filters: departure date range, minimum rating, equipment package, and documented sort keys: `recommended`, `price_asc`, `price_desc`, `rating_desc`, `departure_asc`. Results must only show tours with a future scheduled departure having seats remaining. Where equipment price is shown, distinguish base price from surcharge and calculate from server-owned schedule/package values.

Success shape: `{items: TourSummary[], page, size, totalItems, totalPages}`. A summary contains `{id, title, destinations, coverImageUrl, durationDays, difficulty, startingPriceVnd, ratingAverage?, reviewCount?}`. Null is allowed when a nullable source field or rating aggregate is absent.

## CU-02: public tour detail

`GET /tours/{tourId}` and `GET /tours/{tourId}/reviews?page&size` are public. Existing detail response is `{summary, description, requiredExperience, includedServices, excludedServices, company:{id,name}, gallery, itinerary, schedules}`. A tour is public only when its status is `published` and its company is currently `approved`; otherwise return 404. Detail schedules include only future `scheduled` departures and must report `availableSpots = capacity - held_seats - confirmed_seats`.

Extend detail with route metadata/URL, cancellation policy, packing requirements, equipment options and their prices when schema support is approved, public company display information, and rating summary. Review pages return `{items:[{id,tourRating,companyRating,content,imageUrls,createdAt,companyReply}],page,size,totalItems,totalPages,averageTourRating}` and only include `visible` reviews; omit author/account identifiers and moderation details. Do not expose company contacts/tax/license data, schedule contact phone/meeting contact, moderation fields, or participant data. Locations remain available from `GET /locations` as `{id,name,provinceName,latitude,longitude}[]`.

Tour detail also returns `cancellationRefundRules:[{daysBeforeDeparture,refundPercent}]`, sorted from the longest notice to 0 days. The rules are captured in each booking's policy snapshot at booking time so later tour edits do not change existing bookings. A partner-initiated departure cancellation qualifies for a full refund.

## PA-01: partner tour authoring

| Method and path | Access | Purpose |
|---|---|---|
| GET `/partner/tours?page&size&status` | Active company member | List tours owned by caller’s company. |
| GET `/partner/tours/policy-versions` | Active company member | List active cancellation-policy versions for the caller’s company. |
| POST `/partner/tours` | Company owner/manager | Create a draft. |
| GET `/partner/tours/{tourId}` | Owning company member | Read the tour, including draft-only fields. |
| PATCH `/partner/tours/{tourId}` | Company owner/manager | Update editable fields; never mass-assign company, status, reviewer, or audit fields. |
| POST `/partner/tours/upload-signature` | Company owner/manager | Get a short-lived, company-folder-scoped Cloudinary signature for direct image upload. The API secret is never returned. |
| DELETE `/partner/tours/{tourId}` | Company owner/manager | Archive the tour. Upcoming departures without held or confirmed seats are canceled; existing booking history is retained. |
| POST `/partner/tours/{tourId}/restore` | Company owner/manager | Restore an archived tour as a draft. Canceled departures are not restored; submit the draft for review before it can be public again. |
| POST `/partner/tours/{tourId}/submit` | Company owner/manager | Submit or resubmit for review. |

Editable tour content includes description, media, difficulty, ordered itinerary, route GeoJSON/GPX metadata, included/excluded services, packing items and cancellation policy. Media supports multiple ordered images, with one cover image. Partners upload JPG/PNG/WebP images directly to Cloudinary using the signed upload endpoint; the platform stores their secure URLs. Submission requires at least three Cloudinary-hosted photos and one known destination. Destinations are selected from `/locations` and stored in visit order, which associates the route with known provinces; this is an ordered set of stops, not a mapped GPS track. Validate field lengths, locations and media/route constraints. Ownership must be enforced for every read and write.

## PA-02: moderation workflow

| Method and path | Access | Purpose |
|---|---|---|
| GET `/admin/tours/review?page&size` | Admin | Moderation queue. |
| POST `/admin/tours/{tourId}/approve` | Admin | Approve a submitted tour. |
| POST `/admin/tours/{tourId}/reject` | Admin | Reject with a required reason. |

Lifecycle maps to the current enum values: `draft -> pending -> published | rejected`; rejected tours may be edited and resubmitted. A manager may archive a tour when no upcoming departure has held or confirmed seats; eligible upcoming departures are canceled and booking history remains. An archived tour may be restored only as `draft`, then must be submitted and approved again before returning to public listings. Canceled departures are not restored automatically. Changes to price, cancellation policy, material itinerary or safety content require re-review. Validate transitions server-side, prevent self-review, and record actor/time/reason. Reviewer notes are visible only to authorized partner/admin views.

Partner list response is an explicit DTO `{items:[{id,companyId,title,status,difficulty,durationDays,createdAt,updatedAt}],page,size,totalItems,totalPages}`; do not return framework `Page` serialization. Partner detail is a flat DTO `{id,companyId,title,description,status,difficulty,durationDays,requiredExperience,includedServices,excludedServices,routeFileUrl,cancellationPolicyVersionId,cancellationRefundRules:[{daysBeforeDeparture,refundPercent}],reviewNote?,locationIds,media:[{id,mediaType,url,caption,displayOrder,cover}],itinerary:[{dayNumber,displayOrder,title,description,startTime,locationId}],packingList:[{id,name,required,note,displayOrder}],equipmentOptions,schedules}`. `POST /partner/tours/upload-signature` returns `{cloudName,apiKey,timestamp,folder,allowedFormats,signature}` for a signed upload. Keep `reviewNote` and Cloudinary API secret off every public DTO. Create accepts editable tour content but no `id`, `companyId`, `status`, reviewer or audit fields. Refund rules must contain 1–8 unique day thresholds from 0 to 3650, exactly one 0-day fallback, and refund percentages from 0 to 100 that do not increase as departure gets closer. Patch is partial and rejects unknown/noneditable fields.

Policy version response shape: `{items:[{id,policyTitle,versionNumber,content,effectiveFrom}]}`. Include only active, currently effective cancellation policies belonging to the caller’s company. Create request shape: `{title,description,difficulty,durationDays,requiredExperience,includedServices,excludedServices,routeFileUrl,cancellationPolicyVersionId,cancellationRefundRules,locationIds,media,itinerary,packingList}`. The general cancellation policy is selected by an approved company policy-version ID, never accepted as arbitrary policy text; tour-specific structured refund rules define the refund percentages for that tour. The UI must select a returned policy version and server-side validation must verify company ownership. Patch uses the same fields optionally.

## PA-03 and PA-11: schedule management and operations

| Method and path | Access | Purpose |
|---|---|---|
| POST `/partner/tours/{tourId}/schedules` | Company owner/manager | Create a departure. |
| PATCH `/partner/tours/{tourId}/schedules/{scheduleId}` | Company owner/manager | Update permitted schedule fields. |
| POST `/partner/tours/{tourId}/schedules/{scheduleId}/cancel` | Company owner/manager | Cancel with auditable reason and valid transition. |
| POST `/partner/tours/{tourId}/schedules/{scheduleId}/complete` | Assigned guide or authorized company member | Confirm completion after departure and required checks. |

Schedule fields include start/end timestamps, capacity, base VND price, equipment fee, deposit percent, balance-due deadline, meeting/operational contact details and guide assignments. Preserve held/confirmed seat invariants and reject capacity reductions below occupied seats. Validate guide ownership/eligibility and schedule ownership. Only explicitly authorized roles may confirm completion.

Schedule request shape: `{startsAt,endsAt,capacity,basePriceVnd,equipmentFeeVnd,depositPercent,balanceDueAt,meetingPoint,contactPhone,guideAssignments:[{guideId,isLead}]}`. Schedule response shape: `{id,tourId,startsAt,endsAt,capacity,heldSeats,confirmedSeats,availableSpots,basePriceVnd,equipmentFeeVnd,depositPercent,balanceDueAt,status,meetingPoint,contactPhone,guides:[{id,displayName,isLead}]}`. Private contact fields are partner-only and must never appear in public tour detail.

## PA-04: schedule roster and export

| Method and path | Access | Purpose |
|---|---|---|
| GET `/partner/tours/{tourId}/schedules/{scheduleId}/roster?page&size` | Owning company member | View bookings and passengers for one departure. |
| GET `/partner/tours/{tourId}/schedules/{scheduleId}/roster.xlsx` | Owning company member | Download the same scoped data as Excel. |

Scope by active company membership, tour and schedule before retrieving/exporting rows. Do not expose another company’s passengers. Use safe attachment headers and a non-user-controlled filename.

Roster response shape: `{scheduleId,participants:[{bookingCode,participantId,fullName,phone?,checkedInAt?,checkInNote?}],page,size,totalItems,totalPages}`. Only confirmed/completed/no-show bookings are included. Do not include identity-document numbers, account credentials, payment tokens or other fields not needed for departure operations.

## PA-10: equipment packages

`PUT /partner/tours/{tourId}/equipment-packages` accepts one `{title,includedItems,sharedItems,personalItemsToBring}` descriptive package under the existing schema; public detail may expose those descriptions. Packages are options only and must not track inventory. PA-10 per-person/per-trip price fields and multiple options remain blocked pending schema approval.

**Schema gap requiring approval:** current `tour_equipment_packages` has one descriptive package per tour and no price/basis fields; `tour_schedules` has only one schedule-level equipment fee. It cannot represent multiple package options priced per person or per trip. Do not fake prices or silently change schema. Full PA-10 pricing remains incomplete until the coordinator authorizes a narrow migration and the API contract is amended to match it.

## Implementation status

The current change set adds customer booking holds, PayOS checkout/webhook verification, partner booking/departure/roster/check-in/cancellation workflows, finance admin reconciliation and evidence-backed refund/transfer recording, draft statement generation/approval, and read-only partner statements. Refunds and bank transfers remain external actions. Structured refund percentages are calculated for bookings with a saved rules snapshot; legacy bookings without structured rules retain manual review, and post-statement refund adjustments are not automated. Full PA-10 priced equipment options remain incomplete because the current schema has only one descriptive package and one schedule-level fee; do not present those as multiple priced options. PA-02 moderation is backend-only and is not end-to-end wired into the admin UI. Do not describe these limitations as completed workflows.

## Account profile contract (approved cross-feature scope)

PUT /auth/me is authenticated with a bearer access token and requires the standard CSRF cookie/header handshake. Request: {"fullName":"Nguyen Minh Anh","phone":"+84901234567"}. phone may be an empty string to clear it. fullName is required and at most 200 characters; phone is at most 30 characters. Response is the direct account object {"id":1,"email":"you@example.com","fullName":"Nguyen Minh Anh","phone":"+84901234567","roles": ["customer"]}. Email and roles are read-only. GET /auth/me must return the persisted profile for the current account, including changes made during the current JWT session.
