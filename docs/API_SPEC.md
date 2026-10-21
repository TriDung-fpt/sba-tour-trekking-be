# WeTrek tour API contract — PA-01/02/03/04/10/11 and CU-01/02


This is the shared API contract for the tour task. Backend implementation is scoped to `features/tour/**`. Frontend implementation is scoped to `src/features/tours/**` and the corresponding public/partner tour screens. Changes to auth, account, company, booking, payment, shared security, routing, or other feature packages require coordinator approval. Do not modify another feature to complete a tour story.

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
- No endpoint here creates a booking or payment. Those features have separate owners.

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

Roster response shape: `{scheduleId,participants:[{bookingCode,fullName,phone?,emergencyContact?}]}`. Do not include identity-document numbers, account credentials, payment tokens or other fields not needed for departure operations.

## PA-10: equipment packages

`PUT /partner/tours/{tourId}/equipment-packages` accepts one `{title,includedItems,sharedItems,personalItemsToBring}` descriptive package under the existing schema; public detail may expose those descriptions. Packages are options only and must not track inventory. PA-10 per-person/per-trip price fields and multiple options remain blocked pending schema approval.

**Schema gap requiring approval:** current `tour_equipment_packages` has one descriptive package per tour and no price/basis fields; `tour_schedules` has only one schedule-level equipment fee. It cannot represent multiple package options priced per person or per trip. Do not fake prices or silently change schema. Full PA-10 pricing remains incomplete until the coordinator authorizes a narrow migration and the API contract is amended to match it.

## Implementation status

The current change set adds public search/detail/reviews, partner authoring and schedule operations, moderation endpoints, and roster read/export within `features/tour/**`; the FE adds corresponding tour browse/detail and partner components. Backend clean compilation, frontend lint, final DTO alignment, and the tour-scoped reviewer pass are complete. PA-02 backend moderation endpoints exist, but its FE component is not wired to the existing admin screen because that requires an out-of-scope shared router edit and is pending user authorization; therefore PA-02 is not end-to-end complete. Full PA-10 priced packages remain blocked by the schema gap above. Do not describe either blocked flow as accepted until its endpoint, DTO, authorization and UI work end to end.

## Account profile contract (approved cross-feature scope)

PUT /auth/me is authenticated with a bearer access token and requires the standard CSRF cookie/header handshake. Request: {"fullName":"Nguyen Minh Anh","phone":"+84901234567"}. phone may be an empty string to clear it. fullName is required and at most 200 characters; phone is at most 30 characters. Response is the direct account object {"id":1,"email":"you@example.com","fullName":"Nguyen Minh Anh","phone":"+84901234567","roles": ["customer"]}. Email and roles are read-only. GET /auth/me must return the persisted profile for the current account, including changes made during the current JWT session.
