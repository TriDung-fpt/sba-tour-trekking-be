-- Rename the provider-specific field to a storage-provider-neutral key.
BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE trekking.tour_media RENAME COLUMN cloudinary_public_id TO storage_key;
DO $$
BEGIN
    IF to_regclass('trekking.uq_tour_media_cloudinary_public_id') IS NOT NULL THEN
        ALTER INDEX trekking.uq_tour_media_cloudinary_public_id RENAME TO uq_tour_media_storage_key;
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tour_media_storage_key
    ON trekking.tour_media(storage_key) WHERE storage_key IS NOT NULL;
COMMENT ON COLUMN trekking.tour_media.storage_key IS 'Opaque key used by the configured image storage provider to delete the stored image.';

COMMIT;
