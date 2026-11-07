-- DEV-ONLY cleanup for schema/seed/dev_reviews_seed.sql.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path TO trekking, public;

DELETE FROM trekking.reviews r
USING trekking.bookings b
WHERE r.booking_id = b.id
  AND b.booking_code LIKE 'SEED-REVIEW-%';

DELETE FROM trekking.participants part
USING trekking.bookings b
WHERE part.booking_id = b.id
  AND b.booking_code LIKE 'SEED-REVIEW-%';

DELETE FROM trekking.bookings
WHERE booking_code LIKE 'SEED-REVIEW-%';

DELETE FROM trekking.tour_schedules s
USING trekking.tours t
WHERE s.tour_id = t.id
  AND t.title = '[Seed] Tour Review Sample';

DELETE FROM trekking.tours
WHERE title = '[Seed] Tour Review Sample';

DELETE FROM trekking.policy_versions v
USING trekking.policies p
WHERE v.policy_id = p.id
  AND p.code = 'seed-review-cancel';

DELETE FROM trekking.policies
WHERE code = 'seed-review-cancel';

DELETE FROM trekking.account_roles r
USING trekking.app_accounts a
WHERE r.account_id = a.id
  AND (a.email ILIKE 'review.customer%@seed.local' OR lower(btrim(a.email)) = lower('review.admin@seed.local'));

DELETE FROM trekking.app_accounts
WHERE email ILIKE 'review.customer%@seed.local'
   OR lower(btrim(email)) = lower('review.admin@seed.local');

COMMIT;
