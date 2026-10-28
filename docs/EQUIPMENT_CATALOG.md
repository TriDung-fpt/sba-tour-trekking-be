# Equipment package, stock and handover (EQ-01–EQ-08)

The booking branch owns migrations 007–012 for equipment catalog, rental templates, and pricing. This feature adds migration 013 for the PayOS checkout URL and migration 014 for inventory, reservations, and handover history. Apply them after 012. The package remains one-per-tour, so every schedule under that tour uses the same package. Customers rent equipment only with a tour; standalone rental is not supported.

## Admin catalog and package API

All `/api/v1/admin/**` routes require `TOUR_MANAGE`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/equipment/inventory` | List catalog items with total, maintenance and available quantity |
| PUT | `/api/v1/admin/equipment/{equipmentId}/inventory` | Set stock/category/active values; total stock cannot be reduced below maintenance + commitments |
| GET/POST | `/api/v1/admin/equipment/categories` | List/create categories |
| PUT/DELETE | `/api/v1/admin/equipment/categories/{categoryId}` | Update/delete category (deletion is rejected while assigned) |

Catalog and rental-package CRUD remain in the Tour feature; this Equipment extension owns category/stock, reservation, and handover operations.

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
