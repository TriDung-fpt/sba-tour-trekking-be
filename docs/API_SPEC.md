# WeTrek tour API contract — PA-01/02/03/04/10/11 and CU-01/02

## Partner operations MVP (PO-01)

This section defines partner booking, departure, roster, check-in, cancellation, and statement reads. Customer booking creation and payment collection are defined below. All partner endpoints require an active owner/manager membership in an approved company, except check-in, which also permits a guide assigned to that departure. A company ID supplied by the client is never trusted. PII is limited to what is needed to operate the trip.

| Method and path | Access | Purpose |
| --- | --- | --- |
| GET `/partner/bookings?status&dateFrom&dateTo&query&page&size` | Company owner/manager | List that company's bookings, optionally filter by status, departure date range, or booking code/customer name. |
| GET `/partner/bookings/{bookingId}` | Company owner/manager | Read booking, departure, minimal customer contact, participant list, payment totals, and refund request summaries. |
| GET `/partner/departures?from&to&status&page&size` | Company owner/manager | Read company departures for day-to-day operations without fetching each tour separately. |
| GET `/partner/statements?page&size` | Company owner/manager | Read settlement statements and recorded transfers; partner access is read-only. The current backend has no statement-generation or transfer-recording endpoint. |
| GET `/partner/guides` | Company owner/manager | List active guide accounts for the caller's approved companies, for assignment controls. |
| GET `/partner/tours/{tourId}/schedules/{scheduleId}/roster?page&size` | Owning company member | Read confirmed/completed/no-show booking participants; expired and pending holds are excluded. |
| POST `/partner/bookings/{bookingId}/participants/{participantId}/check-in` | Active company member or assigned guide | Check in one participant; request `{note?}`. Repeating check-in is idempotent and returns the existing check-in. |
| DELETE `/partner/bookings/{bookingId}/participants/{participantId}/check-in` | Company owner/manager | Undo a mistaken check-in before the schedule ends; request `{reason}` and record an audit event. |
| POST `/partner/bookings/{bookingId}/cancel` | Company owner/manager | Request cancellation of a booking with `{reason}`. Server validates lifecycle and records actor/reason. It does not create a refund or claim a refund is complete. |

Booking list response: `{items:[{id,bookingCode,status,createdAt,departure:{scheduleId,tourId,tourTitle,startsAt,endsAt},participantCount,totalPriceVnd,receivedVnd,pendingReviewVnd,refundedVnd}],page,size,totalItems,totalPages}`. Statuses are `pending_payment`, `confirmed`, `expired`, `cancelled`, `completed`, `no_show`. Pending holds past `holdExpiresAt` are shown as `expired` in reads and do not count as available seats; background expiry owns the persisted transition.

Booking detail response: `{id,bookingCode,status,createdAt,departure:{scheduleId,tourId,tourTitle,startsAt,endsAt,meetingPoint,contactPhone},customer:{displayName,email,phone},participants:[{id,fullName,email?,phone?,checkedInAt?,checkInNote?}],money:{totalPriceVnd,receivedVnd,pendingReviewVnd,refundedVnd,depositPercent,balanceDueAt},refundRequests:[{id,requestedAmountVnd,approvedAmountVnd?,status,reason,decisionNote?,createdAt}]}`. Partner detail may show participant contact data only for confirmed bookings and members authorized for trip operations. `receivedVnd` includes accepted payments; funds with `pending_review` allocation are reported separately as `pendingReviewVnd`. Do not expose transaction references, evidence URLs, account IDs, payment tokens, gateway payloads, or private cancellation policy internals.

Departures response: `{items:[{scheduleId,tourId,tourTitle,startsAt,endsAt,status,meetingPoint,contactPhone,capacity,heldSeats,confirmedSeats,availableSeats,participantCount,guideNames}],page,size,totalItems,totalPages}`. Include the caller's approved-company departures with status `scheduled`, `ongoing`, or `completed` by default, filtered by ISO instant `from` inclusive and `to` exclusive; default window is now minus one day through 90 days later. `status=cancelled` can explicitly request cancelled departures. Statements response: `{items:[{id,periodStart,periodEnd,status,payableVnd,transferredVnd,remainingVnd,transfers:[{amountVnd,transferredAt}],note}],page,size,totalItems,totalPages}`; statuses are `draft`, `approved`, `paying`, `paid`, `failed`. Do not expose bank references or evidence URLs. Statement and transfer rows are read-only to partner users. The current backend does not yet provide admin reconciliation, statement generation, or transfer-recording endpoints; no bank transfer is initiated by the platform.

Guide options response: `{items:[{id,companyId,displayName,active}]}`. Only active guides whose account is active and whose company is approved are returned.

Cancellation is allowed only for `pending_payment` or `confirmed` bookings before departure starts, when there is no active payment hold ambiguity; a confirmed cancellation updates the booking state but does not itself calculate or execute a refund. An expired hold is not cancellable. Company members cannot transition bookings to `completed` or `no_show`; schedule completion and customer attendance remain separate audited operations. Refund requests are customer/admin initiated under the saved policy. Partners can see their status and decision note only; they cannot approve, reject, or mark a refund paid. Only a verified gateway webhook or an authorized admin manual verification may create a received payment. Refund transactions are recorded only after the actual transfer is verified and evidenced.

Pagination is zero-based, defaults to `page=0&size=20`, and `size` is limited to 1–100. Dates are ISO-8601 instants. List search is case-insensitive and bounded; invalid status/date windows return 400. Missing or non-owned booking/participant IDs return 404. Invalid transitions return 409. Mutations run transactionally, lock the booking/participant row where competing updates could conflict, and write an audit record. Check-in is idempotent; cancel requires a reason and repeated cancellation returns the current state without duplicating audit side effects.

## Customer booking creation (BK-01)

| Method and path | Access | Purpose |
| --- | --- | --- |
| POST `/bookings` | Signed-in customer + `Idempotency-Key` header | Atomically hold seats, snapshot price and policy, create booking and participants. |

Create request: `{scheduleId,participantCount,includesEquipment,participants:[{fullName,email?,phone?,accountId?}]}`. `participantCount` must equal the participant array size (1–20). The server locks the schedule row, validates that the tour is published, company approved, departure scheduled and future, and capacity remains; calculates price/deposit from schedule values; snapshots the selected cancellation policy and current company agreement; writes an idempotent booking and participants; increments `held_seats`; sets `status=pending_payment` and `holdExpiresAt=now+15 minutes`. Idempotency is scoped to the authenticated account: a repeated key with the same normalized request returns the original booking, while reuse with a different request returns 409. Success is `201` with booking detail `{id,bookingCode,status,holdExpiresAt,participantCount,totalPriceVnd,depositDueVnd,balanceDueAt,participants:[{id,fullName,email?,phone?}]}`. Do not accept price, deposit, company, customer, booking status, policy snapshot, hold duration or received payment from the client.

This endpoint creates a seat hold. A payment attempt is not a payment. The PayOS checkout endpoints below are available, but return `503 PAYMENT_PROVIDER_NOT_CONFIGURED` until merchant credentials and return/cancel URLs are configured. Only a signature-verified PayOS webhook may insert a received payment and confirm a booking. A scheduled expiry processor releases expired holds transactionally and idempotently. Refund processing remains admin/provider controlled; partners only see refund status as described above.

### PayOS checkout and payment status

| Method and path | Access | Purpose |
| --- | --- | --- |
| POST `/bookings/{bookingId}/payment-links` | Booking owner | Create a PayOS checkout link for a due deposit, full payment, or confirmed-booking balance. |
| GET `/bookings/{bookingId}/payment-status` | Booking owner | Read the current booking payment totals and attempt history. |
| POST `/payments/payos/webhook` | Public provider callback | Verify PayOS signature and idempotently record successful payments. |

Create-link request: `{purpose:"deposit"|"full"|"balance"}`. A pending booking accepts `deposit` or `full`; a confirmed booking accepts `balance`. Amounts and expiry are derived from the booking snapshot and current accepted ledger rows. Response: `{enabled,attemptId,purpose,amountVnd,status,checkoutUrl,qrCode,expiresAt}`. `qrCode` is the raw PayOS EMVCo/VietQR payload string. Return and cancel URLs include `paymentReturn=success|cancelled` and `bookingId`; redirects are not proof of payment. A provider creation failure returns a `failed` attempt without a checkout URL; an already active attempt of the same purpose returns 409. Status response: `{paymentEnabled,bookingStatus,totalPriceVnd,receivedVnd,pendingReviewVnd,refundedVnd,amountDueVnd,attempts:[{id,purpose,amountVnd,status,expiresAt}]}`. `receivedVnd` includes accepted payments; `pendingReviewVnd` reports a real but unreconciled amount. Pending attempts are not funds received.

Configure `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_RETURN_URL`, and `PAYOS_CANCEL_URL` through the backend environment. The SDK verifies the signed webhook before ledger changes. A valid success with an unexpected amount or a booking that can no longer be confirmed is recorded for review rather than confirming seats. Duplicate notices are idempotent. No credentials are committed in the repository. Refund execution, admin manual payment verification, statement generation, and transfer recording remain unavailable; no refund or bank transfer is initiated by these endpoints.


This is the shared API contract for tour discovery and partner operations. Backend implementation is scoped to `features/tour/**` for tour authoring/departures and `features/booking/**` for customer booking and partner booking operations. Partner statement reads are in `features/statement/**`. Payment collection supports PayOS checkout and signed webhook verification when configured; refund execution and statement creation/transfer recording are not implemented and must not be shown as available.

### Payment and settlement implementation boundary

The current backend can create an idempotent 15-minute seat hold, create PayOS checkout links when configured, verify PayOS webhook signatures, and display ledger rows recorded in `customer_payments`, `customer_refunds`, `company_statements`, and `company_transfers`. It has no admin manual payment verification endpoint, refund execution endpoint, statement generation endpoint, or admin transfer-recording endpoint yet. Live collection requires merchant credentials and a reachable webhook URL configured in PayOS. Never infer payment/transfer success from a booking, payment attempt, provider redirect, partner action, or front-end result. Keep partner statement and booking financial surfaces read-only.

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
- Booking creation and PayOS payment verification are defined by BK-01 above. No endpoint here executes refunds or initiates partner transfers.

## CU-01: public search, filters and sort

`GET /tours?query&destinationId&difficulty&dateFrom&dateTo&minPrice&maxPrice&minRating&equipmentPackageId&sort&page&size`

Public. Existing implementation already supports `query`, `destinationId`, `difficulty`, `minPrice`, `maxPrice`, `page`, and `size`. Text searches title/description. Difficulty accepts exact lowercase `easy`, `moderate`, `hard`, or `expert`. Price is nonnegative VND and currently filters the lowest upcoming schedule base price. `page` is zero-based (default 0, max 10,000); `size` defaults to 20 and is bounded to 1–100. Invalid values and `minPrice > maxPrice` return 400.

Remaining CU-01 filters: departure date range, minimum rating, equipment package, and documented sort keys: `recommended`, `price_asc`, `price_desc`, `rating_desc`, `departure_asc`. Results must only show tours with a future scheduled departure having seats remaining. Where equipment price is shown, distinguish base price from surcharge and calculate from server-owned schedule/package values.

Success shape: `{items: TourSummary[], page, size, totalItems, totalPages}`. A summary contains `{id, title, destinations, coverImageUrl, durationDays, difficulty, startingPriceVnd, ratingAverage?, reviewCount?}`. Null is allowed when a nullable source field or rating aggregate is absent.

## CU-02: public tour detail

`GET /tours/{tourId}` and `GET /tours/{tourId}/reviews?page&size` are public. Existing detail response is `{summary, description, requiredExperience, includedServices, excludedServices, company:{id,name}, gallery, itinerary, schedules}`. A tour is public only when its status is `published` and its company is currently `approved`; otherwise return 404. Detail schedules include only future `scheduled` departures and must report `availableSpots = capacity - held_seats - confirmed_seats`.

Extend detail with route metadata/URL, cancellation policy, packing requirements, equipment options and their prices when schema support is approved, public company display information, and rating summary. Review pages return `{items:[{id,tourRating,companyRating,content,imageUrls,createdAt,companyReply}],page,size,totalItems,totalPages,averageTourRating}` and only include `visible` reviews; omit author/account identifiers and moderation details. Do not expose company contacts/tax/license data, schedule contact phone/meeting contact, moderation fields, or participant data. Locations remain available from `GET /locations` as `{id,name,provinceName,latitude,longitude}[]`.

## PA-01: partner tour authoring

| Method and path | Access | Purpose |
|---|---|---|
| GET `/partner/tours?page&size&status` | Active company member | List tours owned by caller’s company. |
| GET `/partner/tours/policy-versions` | Active company member | List active cancellation-policy versions for the caller’s company. |
| POST `/partner/tours` | Company owner/manager | Create a draft. |
| GET `/partner/tours/{tourId}` | Owning company member | Read the tour, including draft-only fields. |
| PATCH `/partner/tours/{tourId}` | Company owner/manager | Update editable fields; never mass-assign company, status, reviewer, or audit fields. |
| DELETE `/partner/tours/{tourId}` | Company owner/manager | Archive the tour. Upcoming departures without held or confirmed seats are canceled; existing booking history is retained. |
| POST `/partner/tours/{tourId}/restore` | Company owner/manager | Restore an archived tour as a draft. Canceled departures are not restored; submit the draft for review before it can be public again. |
| POST `/partner/tours/{tourId}/submit` | Company owner/manager | Submit or resubmit for review. |

Editable tour content includes description, media, difficulty, ordered itinerary, route GeoJSON/GPX metadata, included/excluded services, packing items and cancellation policy. Validate field lengths, locations and media/route constraints. Ownership must be enforced for every read and write.

## PA-02: moderation workflow

| Method and path | Access | Purpose |
|---|---|---|
| GET `/admin/tours/review?page&size` | Admin | Moderation queue. |
| POST `/admin/tours/{tourId}/approve` | Admin | Approve a submitted tour. |
| POST `/admin/tours/{tourId}/reject` | Admin | Reject with a required reason. |

Lifecycle maps to the current enum values: `draft -> pending -> published | rejected`; rejected tours may be edited and resubmitted. A manager may archive a tour when no upcoming departure has held or confirmed seats; eligible upcoming departures are canceled and booking history remains. An archived tour may be restored only as `draft`, then must be submitted and approved again before returning to public listings. Canceled departures are not restored automatically. Changes to price, cancellation policy, material itinerary or safety content require re-review. Validate transitions server-side, prevent self-review, and record actor/time/reason. Reviewer notes are visible only to authorized partner/admin views.

Partner list response is an explicit DTO `{items:[{id,companyId,title,status,difficulty,durationDays,createdAt,updatedAt}],page,size,totalItems,totalPages}`; do not return framework `Page` serialization. Partner detail is a flat DTO `{id,companyId,title,description,status,difficulty,durationDays,requiredExperience,includedServices,excludedServices,routeFileUrl,cancellationPolicyVersionId,reviewNote?,locationIds,media:[{id,mediaType,url,caption,displayOrder,cover}],itinerary:[{dayNumber,displayOrder,title,description,startTime,locationId}],packingList:[{id,name,required,note,displayOrder}],equipmentOptions,schedules}`. Keep `reviewNote` off every public DTO. Create accepts editable tour content but no `id`, `companyId`, `status`, reviewer or audit fields. Patch is partial and rejects unknown/noneditable fields.

Policy version response shape: `{items:[{id,policyTitle,versionNumber,content,effectiveFrom}]}`. Include only active, currently effective cancellation policies belonging to the caller’s company. Create request shape: `{title,description,difficulty,durationDays,requiredExperience,includedServices,excludedServices,routeFileUrl,cancellationPolicyVersionId,locationIds,media,itinerary,packingList}`. The cancellation policy is selected by an approved company policy-version ID, never accepted as arbitrary policy text. The UI must select a returned version ID and server-side validation must verify company ownership. Patch uses the same fields optionally.

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

The current change set adds customer booking holds, PayOS checkout and webhook verification, partner booking/departure/roster/check-in/cancellation workflows, read-only partner statements, and frontend customer/partner screens. Refund execution, admin manual payment verification, statement generation, and transfer recording are not implemented. Full PA-10 priced equipment options remain incomplete because the current schema has only one descriptive package and one schedule-level fee; do not present those as multiple priced options. PA-02 moderation is backend-only and is not end-to-end wired into the admin UI. Do not describe these limitations as completed workflows.

## Account profile contract (approved cross-feature scope)

PUT /auth/me is authenticated with a bearer access token and requires the standard CSRF cookie/header handshake. Request: {"fullName":"Nguyen Minh Anh","phone":"+84901234567"}. phone may be an empty string to clear it. fullName is required and at most 200 characters; phone is at most 30 characters. Response is the direct account object {"id":1,"email":"you@example.com","fullName":"Nguyen Minh Anh","phone":"+84901234567","roles": ["customer"]}. Email and roles are read-only. GET /auth/me must return the persisted profile for the current account, including changes made during the current JWT session.
