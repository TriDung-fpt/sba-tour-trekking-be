-- Apply after 007. Existing media URLs remain valid with a NULL Cloudinary ID.
-- IF NOT EXISTS also supports fresh databases initialized from the updated schema.sql.
BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE trekking.tour_media ADD COLUMN IF NOT EXISTS cloudinary_public_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tour_media_cloudinary_public_id
    ON trekking.tour_media(cloudinary_public_id) WHERE cloudinary_public_id IS NOT NULL;
COMMENT ON COLUMN trekking.tour_media.cloudinary_public_id IS
    'Cloudinary public_id used to delete an uploaded image; NULL for legacy media URLs.';

COMMIT;
