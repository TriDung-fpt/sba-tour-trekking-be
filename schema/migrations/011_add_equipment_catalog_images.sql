-- Add one optional image to each reusable equipment item and rental package template.
BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE trekking.equipment
    ADD COLUMN image_url TEXT,
    ADD COLUMN storage_key TEXT;

ALTER TABLE trekking.rental_package_templates
    ADD COLUMN image_url TEXT,
    ADD COLUMN storage_key TEXT;

CREATE UNIQUE INDEX uq_equipment_storage_key
    ON trekking.equipment(storage_key) WHERE storage_key IS NOT NULL;
CREATE UNIQUE INDEX uq_rental_package_templates_storage_key
    ON trekking.rental_package_templates(storage_key) WHERE storage_key IS NOT NULL;

COMMENT ON COLUMN trekking.equipment.image_url IS 'Public URL of the equipment image.';
COMMENT ON COLUMN trekking.equipment.storage_key IS 'Opaque image storage key used to replace or delete the image.';
COMMENT ON COLUMN trekking.rental_package_templates.image_url IS 'Public URL of the rental package image.';
COMMENT ON COLUMN trekking.rental_package_templates.storage_key IS 'Opaque image storage key used to replace or delete the image.';

COMMIT;
