# Equipment package, stock and handover (EQ-01–EQ-08)

The booking branch owns migrations 007–012 for equipment catalog, rental templates, and pricing. This feature adds migration 013 for the PayOS checkout URL, migration 014 for inventory, reservations, and handover history, and migration 021 for attaching multiple rental package combos and multiple standalone individual equipment items per tour with optimistic concurrency protection (`equipment_config_version`) and historical reservation preservation. Customers rent equipment only with a tour; standalone rental is not supported.

## Admin catalog and package API

All `/api/v1/admin/**` routes require `TOUR_MANAGE`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/equipment/inventory` | List catalog items with total, maintenance and available quantity |
| PUT | `/api/v1/admin/equipment/{equipmentId}/inventory` | Set stock/category/active values; total stock cannot be reduced below maintenance + commitments |
| GET/POST | `/api/v1/admin/equipment/categories` | List/create categories |
| PUT/DELETE | `/api/v1/admin/equipment/categories/{categoryId}` | Update/delete category (deletion is rejected while assigned) |
| GET/PUT | `/api/v1/tours/{tourId}/equipment` | Read or replace the tour's attached rental package combos (`packages` / `packageTemplateIds`) and standalone individual rental items (`individualItems` / `items` / `individualEquipmentIds`) |

Catalog and rental-package CRUD remain in the Tour feature; this Equipment extension owns category/stock, reservation, and handover operations.

### Tour equipment configuration (`GET/PUT /api/v1/tours/{tourId}/equipment`)

- `PUT /api/v1/tours/{tourId}/equipment` accepts `version` (`Long`), `packageTemplateIds` (`List<Long>`, with legacy single `packageTemplateId` still accepted for backward compatibility), and `individualEquipmentIds` (`List<Long>`).
- **Optimistic concurrency (`version`)**:
  - `GET /api/v1/tours/{tourId}/equipment` returns `version` (`Long`, starting at `0` for unconfigured tours and incremented on every successful `PUT`).
  - When updating an existing configuration (`version > 0`), the client must pass the current `version` in `PUT /api/v1/tours/{tourId}/equipment`. If another admin session saved in the meantime (`version` mismatch) or if an older client omits `version` when `version > 0`, the backend rejects the request with `409 CONFLICT` (`EQUIPMENT_CONFIG_CONFLICT`) and leaves the newer configuration untouched.
- A tour can offer multiple combos and multiple individual items simultaneously.
- An equipment item included inside one or more selected combos can also be selected in `individualEquipmentIds` for standalone rental on the same tour.
- Duplicate combo IDs inside `packageTemplateIds` or duplicate equipment IDs inside `individualEquipmentIds`, unknown IDs, or individual items without an `individualRentalPriceVnd` are rejected with `400 BAD_REQUEST` (`INVALID_EQUIPMENT_SELECTION`) and roll back atomically.
- **Response contract & backward compatibility**:
  - `GET /api/v1/tours/{tourId}/equipment` (and `/api/v1/public/tours/{tourId}/equipment`) returns:
    - `version`: current configuration version (`Long`).
    - `needsReview`: `true` if any migrated legacy package on the tour could not be auto-classified unambiguously (for example, because its combo template was modified after being attached to the tour) and requires admin review before re-saving.
    - `packages`: list of active attached combos (`id`, `packageTemplateId`, `title`, `includedItems`, `rentalPriceVnd`, `needsReview`, and `items` belonging to that combo).
    - `individualItems` and `items`: standalone individual rental items for the tour (`package_id IS NULL`). Note: `items` contains **only** standalone individual rental items and does not duplicate items inside `packages[].items`.
    - `equipmentPackage`: first combo in `packages` or `null` for single-package backward compatibility.
- **Template edits vs. tour snapshots**:
  - Attaching a combo copies its title, summary, price, and item rows into the tour's snapshot (`tour_equipment_packages` and `tour_equipment_items`). Editing a combo template in `/api/v1/tours/rental-packages` does not automatically mutate existing tour snapshots until an admin re-saves the tour's equipment configuration.
- **Historical reservation preservation**:
  - `equipment_reservations.package_id` retains `ON DELETE RESTRICT`. If a `tour_equipment_packages` row is referenced by historical reservations and an admin later removes that combo or updates it after a template change, the historical package row and its `tour_equipment_items` are archived (`active = false`) rather than deleted or mutated in place.

Rental package requests and responses no longer include the legacy `sharedItems` or `personalItemsToBring` notes. Package contents are represented by equipment items and quantities. The corresponding nullable database columns remain in place to preserve any historical values from existing installations; they are no longer read or updated by the application.

The Booking selection modes are `NONE`, `PACKAGE`, and `ITEMS`. `PACKAGE` holds each package item's `quantityInPackage` multiplied by the number of packages selected. `ITEMS` holds the selected `tour_equipment_items` quantities. Rental prices are per package or per item for the whole tour; neither is multiplied by participant count. The hold service accepts item IDs from `tour_equipment_items`, checks stock, and stores a snapshot. Booking owns the customer-facing price calculation and saves its group rental fee/snapshot.

## Booking integration contract

`EquipmentReservationService` is the internal API for Booking. Call `hold(EquipmentHoldRequest)` after saving the booking and before committing its transaction. The request includes `mode`, schedule/booking IDs, package ID and package quantity for `PACKAGE`, or item IDs/quantities for `ITEMS`; participant count is stored for reference and does not multiply equipment. `NONE` creates no stock reservation. The service checks stock for overlapping schedule date ranges while locking equipment rows and stores a selection/content snapshot. Its fee result is for consistency; Booking remains the source of truth for its group rental amount and customer snapshot.

Call `release(bookingId)` in the same transaction when Booking cancels or expires. This is idempotent. Expired `held` reservations are also released by a scheduled task. On an accepted successful PayOS payment, Payment calls `confirm(bookingId)`; this only confirms the equipment reservation and does not decide Booking status. Booking compares payment state according to its own lifecycle. If equipment has already been issued, cancellation moves the reservation to `return_required`; issued stock remains committed until returned.

The hold/confirm/release methods require an existing transaction (`MANDATORY`) to keep Booking, payment, and stock changes atomic. Booking must handle a hold conflict by rejecting/rolling back its own booking creation. Stock availability returned in the admin list is informational; `hold()` performs the authoritative atomic check.

## Issue and return API (EQ-07)

Admin (`TOUR_MANAGE`) or the guide assigned to the booking schedule can view and record handovers:

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/bookings/{bookingId}/equipment/reservation` | Read reservation state and item counts |
| POST | `/api/v1/bookings/{bookingId}/equipment/issue` | Record quantities issued to the tour |
| POST | `/api/v1/bookings/{bookingId}/equipment/return` | Record returned quantities and condition (`GOOD` or `NEEDS_INSPECTION`) |

Return quantities cannot exceed outstanding issued quantities. `NEEDS_INSPECTION` moves the quantity into maintenance stock; `GOOD` stock becomes available when returned. Every operation records actor, time, note, quantities, and condition. Current authorization supports ADMIN and assigned GUIDE roles; if the product requires a separate staff role, authorization needs a coordinated role/permission change.
