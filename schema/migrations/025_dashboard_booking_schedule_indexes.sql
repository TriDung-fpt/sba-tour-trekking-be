-- Range scans for the admin dashboard: booking date and departure date.
BEGIN;
CREATE INDEX IF NOT EXISTS ix_bookings_created_at ON trekking.bookings (created_at);
CREATE INDEX IF NOT EXISTS ix_tour_schedules_starts_at ON trekking.tour_schedules (starts_at);
COMMIT;
