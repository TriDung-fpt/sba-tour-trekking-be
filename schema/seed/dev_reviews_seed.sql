-- DEV-ONLY sample reviews. Idempotent. Do not run on production.
-- All seed accounts use @seed.local. Fixture satisfies validate_review trigger.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL search_path TO trekking, public;

-- Password: SeedPassword1! (bcrypt cost 12). Not for production use.
CREATE TEMP TABLE seed_review_plan (
    seq INT PRIMARY KEY,
    customer_email TEXT NOT NULL,
    customer_name TEXT NOT NULL,
    booking_code TEXT NOT NULL,
    tour_rating SMALLINT NOT NULL,
    service_rating SMALLINT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL,
    moderation_reason TEXT,
    created_offset INTERVAL NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_review_plan(seq, customer_email, customer_name, booking_code, tour_rating, service_rating,
                             content, status, moderation_reason, created_offset) VALUES
(1,  'review.customer01@seed.local', 'Khách Seed 01', 'SEED-REVIEW-01', 5, 5, 'Tour rất tuyệt, hướng dẫn nhiệt tình.', 'visible', NULL, interval '90 days'),
(2,  'review.customer02@seed.local', 'Khách Seed 02', 'SEED-REVIEW-02', 4, 5, 'Cảnh đẹp, lịch trình hợp lý.', 'visible', NULL, interval '80 days'),
(3,  'review.customer03@seed.local', 'Khách Seed 03', 'SEED-REVIEW-03', 3, 4, 'Ổn nhưng thời tiết hơi khắc nghiệt.', 'visible', NULL, interval '70 days'),
(4,  'review.customer04@seed.local', 'Khách Seed 04', 'SEED-REVIEW-04', 2, 3, 'Đường đi khá mệt so với mô tả.', 'visible', NULL, interval '60 days'),
(5,  'review.customer05@seed.local', 'Khách Seed 05', 'SEED-REVIEW-05', 1, 2, 'Trải nghiệm chưa như kỳ vọng.', 'hidden', 'Nội dung tiêu cực chưa xác minh', interval '55 days'),
(6,  'review.customer06@seed.local', 'Khách Seed 06', 'SEED-REVIEW-06', 5, 4, 'Đáng tiền, sẽ giới thiệu bạn bè.', 'visible', NULL, interval '50 days'),
(7,  'review.customer07@seed.local', 'Khách Seed 07', 'SEED-REVIEW-07', 4, 4, 'Guide hỗ trợ tốt suốt chuyến.', 'visible', NULL, interval '45 days'),
(8,  'review.customer08@seed.local', 'Khách Seed 08', 'SEED-REVIEW-08', 3, 3, 'Tạm được, chỗ nghỉ bình thường.', 'visible', NULL, interval '40 days'),
(9,  'review.customer09@seed.local', 'Khách Seed 09', 'SEED-REVIEW-09', 5, 5, 'Một trong những chuyến hay nhất.', 'visible', NULL, interval '35 days'),
(10, 'review.customer10@seed.local', 'Khách Seed 10', 'SEED-REVIEW-10', 2, 2, 'Thiếu thông tin chuẩn bị trước tour.', 'hidden', 'Spam / nội dung không phù hợp', interval '30 days'),
(11, 'review.customer11@seed.local', 'Khách Seed 11', 'SEED-REVIEW-11', 4, 3, 'Phong cảnh đẹp, đồ ăn ổn.', 'visible', NULL, interval '20 days'),
(12, 'review.customer12@seed.local', 'Khách Seed 12', 'SEED-REVIEW-12', 5, 4, 'Check-in nhanh, đoàn vui vẻ.', 'visible', NULL, interval '15 days'),
(13, 'review.customer13@seed.local', 'Khách Seed 13', 'SEED-REVIEW-13', 1, 1, 'Không hài lòng về tổ chức.', 'hidden', 'Ngôn từ thiếu xây dựng', interval '10 days'),
(14, 'review.customer14@seed.local', 'Khách Seed 14', 'SEED-REVIEW-14', 3, 5, 'Dịch vụ tốt dù đường hơi khó.', 'visible', NULL, interval '5 days');

INSERT INTO trekking.app_accounts(email, password_hash, full_name, phone, status, email_verified_at)
SELECT 'review.admin@seed.local',
       '$2y$12$TNU7i.z1mB6ChOGWwRlr0eF9Xwupll6gS7DE3e2xEMT1h85QDhcxC',
       'Admin Review Seed',
       '0900000000',
       'active',
       CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM trekking.app_accounts WHERE lower(btrim(email)) = lower('review.admin@seed.local')
);

INSERT INTO trekking.account_roles(account_id, role)
SELECT a.id, 'admin'
FROM trekking.app_accounts a
WHERE lower(btrim(a.email)) = lower('review.admin@seed.local')
  AND NOT EXISTS (
      SELECT 1 FROM trekking.account_roles r WHERE r.account_id = a.id AND r.role = 'admin'
  );

INSERT INTO trekking.app_accounts(email, password_hash, full_name, phone, status, email_verified_at)
SELECT p.customer_email,
       '$2y$12$TNU7i.z1mB6ChOGWwRlr0eF9Xwupll6gS7DE3e2xEMT1h85QDhcxC',
       p.customer_name,
       '09000000' || lpad(p.seq::text, 2, '0'),
       'active',
       CURRENT_TIMESTAMP
FROM seed_review_plan p
WHERE NOT EXISTS (
    SELECT 1 FROM trekking.app_accounts a WHERE lower(btrim(a.email)) = lower(p.customer_email)
);

INSERT INTO trekking.account_roles(account_id, role)
SELECT a.id, 'customer'
FROM seed_review_plan p
JOIN trekking.app_accounts a ON lower(btrim(a.email)) = lower(p.customer_email)
WHERE NOT EXISTS (
    SELECT 1 FROM trekking.account_roles r WHERE r.account_id = a.id AND r.role = 'customer'
);

INSERT INTO trekking.policies(code, title, kind, created_by)
SELECT 'seed-review-cancel', 'Seed Review Cancellation', 'cancellation', a.id
FROM trekking.app_accounts a
WHERE lower(btrim(a.email)) = lower('review.admin@seed.local')
  AND NOT EXISTS (SELECT 1 FROM trekking.policies WHERE code = 'seed-review-cancel');

INSERT INTO trekking.policy_versions(policy_id, version_number, content, effective_from)
SELECT p.id, 1, 'Seed cancellation terms for review fixtures.', CURRENT_TIMESTAMP - interval '120 days'
FROM trekking.policies p
WHERE p.code = 'seed-review-cancel'
  AND NOT EXISTS (
      SELECT 1 FROM trekking.policy_versions v WHERE v.policy_id = p.id AND v.version_number = 1
  );

INSERT INTO trekking.tours(title, description, difficulty, duration_days, status,
                           cancellation_policy_version_id, reviewed_by, reviewed_at)
SELECT '[Seed] Tour Review Sample',
       'Tour mẫu phục vụ seed review admin. Email @seed.local.',
       'moderate',
       2,
       'published',
       v.id,
       admin.id,
       CURRENT_TIMESTAMP - interval '100 days'
FROM trekking.policies p
JOIN trekking.policy_versions v ON v.policy_id = p.id AND v.version_number = 1
JOIN trekking.app_accounts admin ON lower(btrim(admin.email)) = lower('review.admin@seed.local')
WHERE p.code = 'seed-review-cancel'
  AND NOT EXISTS (
      SELECT 1 FROM trekking.tours t WHERE t.title = '[Seed] Tour Review Sample'
  );

INSERT INTO trekking.tour_schedules(tour_id, starts_at, ends_at, capacity, confirmed_seats,
                                     base_price_vnd, deposit_percent, balance_due_at,
                                     status, actual_completed_at, completed_by)
SELECT t.id,
       CURRENT_TIMESTAMP - interval '95 days',
       CURRENT_TIMESTAMP - interval '93 days',
       40,
       14,
       1500000,
       30,
       CURRENT_TIMESTAMP - interval '100 days',
       'completed',
       CURRENT_TIMESTAMP - interval '92 days',
       admin.id
FROM trekking.tours t
JOIN trekking.app_accounts admin ON lower(btrim(admin.email)) = lower('review.admin@seed.local')
WHERE t.title = '[Seed] Tour Review Sample'
  AND NOT EXISTS (
      SELECT 1 FROM trekking.tour_schedules s
      WHERE s.tour_id = t.id AND s.status = 'completed' AND s.capacity = 40 AND s.confirmed_seats = 14
  );

INSERT INTO trekking.bookings(
    booking_code, idempotency_key, customer_id, schedule_id, participant_count, includes_equipment,
    base_price_vnd, equipment_fee_vnd, deposit_percent, cancellation_policy_version_id, policy_snapshot,
    hold_expires_at, balance_due_at, status)
SELECT p.booking_code,
       'seed-review-key-' || lpad(p.seq::text, 2, '0'),
       customer.id,
       s.id,
       1,
       false,
       1500000,
       0,
       30,
       v.id,
       '{}'::jsonb,
       CURRENT_TIMESTAMP - interval '110 days',
       CURRENT_TIMESTAMP - interval '100 days',
       'completed'
FROM seed_review_plan p
JOIN trekking.app_accounts customer ON lower(btrim(customer.email)) = lower(p.customer_email)
JOIN trekking.tours t ON t.title = '[Seed] Tour Review Sample'
JOIN trekking.tour_schedules s ON s.tour_id = t.id AND s.status = 'completed' AND s.capacity = 40
JOIN trekking.policies pol ON pol.code = 'seed-review-cancel'
JOIN trekking.policy_versions v ON v.policy_id = pol.id AND v.version_number = 1
WHERE NOT EXISTS (
    SELECT 1 FROM trekking.bookings b WHERE b.booking_code = p.booking_code
);

INSERT INTO trekking.participants(booking_id, account_id, full_name, checked_in_at, checked_in_by)
SELECT b.id, b.customer_id, customer.full_name,
       CURRENT_TIMESTAMP - interval '94 days',
       admin.id
FROM seed_review_plan p
JOIN trekking.bookings b ON b.booking_code = p.booking_code
JOIN trekking.app_accounts customer ON customer.id = b.customer_id
JOIN trekking.app_accounts admin ON lower(btrim(admin.email)) = lower('review.admin@seed.local')
WHERE NOT EXISTS (
    SELECT 1 FROM trekking.participants part
    WHERE part.booking_id = b.id AND part.account_id = b.customer_id
);

INSERT INTO trekking.reviews(
    booking_id, author_id, participant_id, tour_rating, service_rating, content, status,
    moderated_by, moderation_reason, created_at, updated_at)
SELECT b.id,
       b.customer_id,
       part.id,
       p.tour_rating,
       p.service_rating,
       p.content,
       p.status,
       CASE WHEN p.status = 'hidden' THEN admin.id ELSE NULL END,
       p.moderation_reason,
       CURRENT_TIMESTAMP - p.created_offset,
       CURRENT_TIMESTAMP - p.created_offset
FROM seed_review_plan p
JOIN trekking.bookings b ON b.booking_code = p.booking_code
JOIN trekking.participants part ON part.booking_id = b.id AND part.account_id = b.customer_id
JOIN trekking.app_accounts admin ON lower(btrim(admin.email)) = lower('review.admin@seed.local')
WHERE NOT EXISTS (
    SELECT 1 FROM trekking.reviews r WHERE r.booking_id = b.id
);

COMMIT;
