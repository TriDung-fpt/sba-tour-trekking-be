-- Indexes for admin review list filters and default created_at sort.
BEGIN;
CREATE INDEX IF NOT EXISTS ix_reviews_status_created_at
    ON trekking.reviews (status, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_reviews_tour_rating
    ON trekking.reviews (tour_rating);
COMMIT;
