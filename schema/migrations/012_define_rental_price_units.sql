-- Rental prices are charged by the selected package/item quantity, not by traveler count.
BEGIN;
SET LOCAL lock_timeout = '10s';

COMMENT ON COLUMN trekking.rental_package_templates.rental_price_vnd IS
    'VND price for renting one complete equipment package for the whole tour; multiply by the number of packages rented.';
COMMENT ON COLUMN trekking.tour_equipment_packages.rental_price_vnd IS
    'VND price for renting one complete equipment package for the whole tour; multiply by the number of packages rented.';
COMMENT ON COLUMN trekking.equipment.individual_rental_price_vnd IS
    'Default VND price for renting one equipment item for the whole tour; multiply by the number of items rented.';
COMMENT ON COLUMN trekking.rental_package_template_items.individual_rental_price_vnd IS
    'Optional override: VND price for renting one equipment item for the whole tour.';
COMMENT ON COLUMN trekking.tour_equipment_items.individual_rental_price_vnd IS
    'VND price for renting one equipment item for the whole tour; multiply by the number of items rented.';

COMMIT;
