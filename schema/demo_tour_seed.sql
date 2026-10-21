-- Idempotent sample catalog data for the WeTrek public homepage.
-- Run from the repository root:
--   docker cp schema/demo_tour_seed.sql trekking-postgres:/tmp/demo_tour_seed.sql
--   docker exec trekking-postgres psql -U postgres -d trekking_db -v ON_ERROR_STOP=1 -f /tmp/demo_tour_seed.sql
--
-- These listings, routes, schedules, and the disabled reviewer are demonstration
-- data. They do not create bookings, payments, or customer review claims.

\encoding UTF8
SET client_encoding TO 'UTF8';

BEGIN;
SET LOCAL search_path TO trekking, public;

-- A disabled fallback reviewer supplies the required historical foreign keys
-- on fresh schema installs. Existing local admin accounts are preferred below.
INSERT INTO app_accounts (email, password_hash, full_name, status, email_verified_at)
SELECT 'seed.reviewer@wetrek.invalid', 'disabled-demo-reviewer-no-login',
       'WeTrek Demo Catalog Reviewer', 'disabled', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM app_accounts WHERE lower(email) = 'admin@trekking.com')
  AND NOT EXISTS (SELECT 1 FROM app_accounts WHERE lower(email) = 'seed.reviewer@wetrek.invalid');

INSERT INTO companies (name, tax_code, representative_name, contact_email, address, description,
                       status, approved_by, approved_at)
SELECT 'WeTrek – Hành trình Việt Nam', 'DEMO-WETREK-001', 'Đội ngũ WeTrek',
       'demo@wetrek.invalid', 'Lào Cai, Việt Nam',
       'Đơn vị tổ chức các hành trình trekking và khám phá thiên nhiên tại Việt Nam.',
       'approved',
       COALESCE(
           (SELECT id FROM app_accounts WHERE lower(email) = 'admin@trekking.com' ORDER BY id LIMIT 1),
           (SELECT id FROM app_accounts WHERE lower(email) = 'seed.reviewer@wetrek.invalid' ORDER BY id LIMIT 1)
       ),
       CURRENT_TIMESTAMP
ON CONFLICT (tax_code) WHERE tax_code IS NOT NULL DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'approved',
    approved_by = EXCLUDED.approved_by,
    approved_at = EXCLUDED.approved_at,
    updated_at = CURRENT_TIMESTAMP;

-- Keep the existing demo records and their foreign keys while localizing them.
UPDATE tours SET title = CASE title
    WHEN 'Fansipan Summit Ridge Trek' THEN 'Chinh phục sống núi Fansipan'
    WHEN 'Ta Xua Cloud Sea Traverse' THEN 'Săn biển mây Tà Xùa'
    WHEN 'Bach Moc Luong Tu Cloud Forest Trek' THEN 'Trekking rừng mây Bạch Mộc Lương Tử'
    WHEN 'Lan Ha Bay Island and Coastal Walk' THEN 'Trekking ven vịnh Lan Hạ'
END
WHERE company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
  AND title IN ('Fansipan Summit Ridge Trek', 'Ta Xua Cloud Sea Traverse',
                'Bach Moc Luong Tu Cloud Forest Trek', 'Lan Ha Bay Island and Coastal Walk');

UPDATE locations SET name = CASE name
    WHEN 'Ta Xua' THEN 'Tà Xùa'
    WHEN 'Bach Moc Luong Tu' THEN 'Bạch Mộc Lương Tử'
    WHEN 'Lan Ha Bay' THEN 'Vịnh Lan Hạ'
    ELSE name END,
    province_name = CASE province_name
    WHEN 'Lao Cai' THEN 'Lào Cai'
    WHEN 'Son La' THEN 'Sơn La'
    WHEN 'Hai Phong' THEN 'Hải Phòng'
    ELSE province_name END
WHERE name IN ('Fansipan', 'Ta Xua', 'Bach Moc Luong Tu', 'Lan Ha Bay');
INSERT INTO policies (code, title, kind, company_id, created_by, active)
SELECT 'DEMO-WETREK-CANCELLATION', 'Chính sách hủy chuyến mẫu', 'cancellation',
       c.id,
       COALESCE(
           (SELECT id FROM app_accounts WHERE lower(email) = 'admin@trekking.com' ORDER BY id LIMIT 1),
           (SELECT id FROM app_accounts WHERE lower(email) = 'seed.reviewer@wetrek.invalid' ORDER BY id LIMIT 1)
       ),
       TRUE
FROM companies c
WHERE c.tax_code = 'DEMO-WETREK-001'
ON CONFLICT (code) DO UPDATE SET
    title = EXCLUDED.title,
    kind = EXCLUDED.kind,
    company_id = EXCLUDED.company_id,
    active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO policy_versions (policy_id, version_number, content, effective_from, approved_by, approved_at)
SELECT p.id, 1,
       'Chính sách mẫu: vui lòng liên hệ đơn vị tổ chức trước ngày khởi hành nếu cần đổi hoặc hủy chuyến. Nội dung này chỉ phục vụ kiểm thử giao diện.',
       CURRENT_TIMESTAMP - INTERVAL '1 day',
       COALESCE(
           (SELECT id FROM app_accounts WHERE lower(email) = 'admin@trekking.com' ORDER BY id LIMIT 1),
           (SELECT id FROM app_accounts WHERE lower(email) = 'seed.reviewer@wetrek.invalid' ORDER BY id LIMIT 1)
       ),
       CURRENT_TIMESTAMP
FROM policies p
WHERE p.code = 'DEMO-WETREK-CANCELLATION'
ON CONFLICT (policy_id, version_number) DO NOTHING;

INSERT INTO policy_versions (policy_id, version_number, content, effective_from, approved_by, approved_at)
SELECT p.id, 2,
       'Chính sách mẫu: vui lòng liên hệ đơn vị tổ chức trước ngày khởi hành nếu cần đổi hoặc hủy chuyến. Nội dung này chỉ phục vụ kiểm thử giao diện.',
       CURRENT_TIMESTAMP - INTERVAL '1 day',
       COALESCE(
           (SELECT id FROM app_accounts WHERE lower(email) = 'admin@trekking.com' ORDER BY id LIMIT 1),
           (SELECT id FROM app_accounts WHERE lower(email) = 'seed.reviewer@wetrek.invalid' ORDER BY id LIMIT 1)
       ),
       CURRENT_TIMESTAMP
FROM policies p
WHERE p.code = 'DEMO-WETREK-CANCELLATION'
ON CONFLICT (policy_id, version_number) DO NOTHING;

INSERT INTO locations (name, province_name, latitude, longitude)
SELECT v.name, v.province_name, v.latitude, v.longitude
FROM (VALUES
    ('Fansipan', 'Lào Cai', 22.3033::numeric, 103.7750::numeric),
    ('Tà Xùa', 'Sơn La', 21.3000::numeric, 104.4500::numeric),
    ('Bạch Mộc Lương Tử', 'Lào Cai', 22.3900::numeric, 103.7000::numeric),
    ('Vịnh Lan Hạ', 'Hải Phòng', 20.7290::numeric, 107.0540::numeric)
) AS v(name, province_name, latitude, longitude)
WHERE NOT EXISTS (
    SELECT 1 FROM locations l
    WHERE l.name = v.name AND l.province_name = v.province_name
);

INSERT INTO tours (company_id, title, description, difficulty, duration_days, required_experience,
                   included_services, excluded_services, status, cancellation_policy_version_id,
                   reviewed_by, reviewed_at)
SELECT c.id, v.title, v.description, v.difficulty, v.duration_days, v.required_experience,
       v.included_services, v.excluded_services, 'published', pv.id,
       COALESCE(
           (SELECT id FROM app_accounts WHERE lower(email) = 'admin@trekking.com' ORDER BY id LIMIT 1),
           (SELECT id FROM app_accounts WHERE lower(email) = 'seed.reviewer@wetrek.invalid' ORDER BY id LIMIT 1)
       ),
       CURRENT_TIMESTAMP
FROM companies c
JOIN policy_versions pv ON pv.policy_id = (
    SELECT id FROM policies WHERE code = 'DEMO-WETREK-CANCELLATION'
) AND pv.version_number = 2
CROSS JOIN (VALUES
    ('Chinh phục sống núi Fansipan', 'Theo chân hướng dẫn viên băng qua những cánh rừng Hoàng Liên Sơn, nghỉ đêm trên núi và đón bình minh tại đỉnh Fansipan.', 'hard', 2,
     'Có thể đi bộ đường dài cả ngày và đã quen với địa hình núi gồ ghề.',
     'Hướng dẫn viên địa phương, hỗ trợ thủ tục vào vườn quốc gia, bộ sơ cứu nhóm, chỗ nghỉ trên núi và nước uống.',
     'Di chuyển đến Lào Cai, bảo hiểm du lịch cá nhân, các bữa ăn không nêu trong lịch trình và trang bị trekking cá nhân.',
     'Fansipan', '/images/demo-fansipan.jpg',
     2900000::bigint, 150000::bigint, 12),
    ('Săn biển mây Tà Xùa', 'Khám phá sống lưng khủng long trên biển mây, các điểm ngắm cảnh trên đỉnh núi và nghỉ tại homestay địa phương.', 'moderate', 2,
     'Có thể đi bộ nhiều giờ trên đường đồi và một số đoạn hẹp.',
     'Hướng dẫn viên địa phương, homestay, phổ biến lộ trình, thiết bị an toàn dùng chung và nước uống.',
     'Di chuyển đến Tà Xùa, bảo hiểm du lịch, bữa ăn cá nhân và trang bị cá nhân.',
     'Tà Xùa', '/images/demo-ta-xua.jpg',
     1850000::bigint, 100000::bigint, 28),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 'Hành trình ba ngày qua rừng trúc và những dãy núi cao để chinh phục đỉnh Bạch Mộc Lương Tử giữa biển mây.', 'expert', 3,
     'Khuyến nghị có thể lực tốt và kinh nghiệm trekking nhiều ngày.',
     'Hướng dẫn viên địa phương đủ điều kiện, đội porter, điểm cắm trại, thiết bị an toàn dùng chung và bữa ăn trên cung đường.',
     'Di chuyển đến điểm xuất phát, bảo hiểm cá nhân và trang bị trekking cá nhân.',
     'Bạch Mộc Lương Tử', '/images/demo-bach-moc.jpg',
     3650000::bigint, 200000::bigint, 45),
    ('Trekking ven vịnh Lan Hạ', 'Cùng hướng dẫn viên khám phá những vịnh nhỏ yên bình và đảo đá vôi, kết hợp các lối đi bộ ven biển và nghỉ đêm gần Cát Bà.', 'easy', 2,
     'Không cần kinh nghiệm trekking; phù hợp với người mới có thể lực cơ bản.',
     'Hướng dẫn viên địa phương, chỗ nghỉ qua đêm, phổ biến lộ trình và nước uống.',
     'Di chuyển đến Cát Bà, bảo hiểm du lịch và các bữa ăn không nêu trong lịch trình.',
     'Vịnh Lan Hạ', '/images/demo-lan-ha.jpg',
     2450000::bigint, 0::bigint, 62)
) AS v(title, description, difficulty, duration_days, required_experience, included_services,
       excluded_services, location_name, cover_url, base_price_vnd, equipment_fee_vnd, start_offset_days)
WHERE c.tax_code = 'DEMO-WETREK-001'
  AND NOT EXISTS (SELECT 1 FROM tours t WHERE t.company_id = c.id AND t.title = v.title);

UPDATE tours t SET cancellation_policy_version_id = (SELECT id FROM policy_versions WHERE policy_id = (SELECT id FROM policies WHERE code = 'DEMO-WETREK-CANCELLATION') AND version_number = 2),
    description = v.description,
    required_experience = v.required_experience,
    included_services = v.included_services,
    excluded_services = v.excluded_services,
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('Chinh phục sống núi Fansipan', 'Theo chân hướng dẫn viên băng qua những cánh rừng Hoàng Liên Sơn, nghỉ đêm trên núi và đón bình minh tại đỉnh Fansipan.', 'Có thể đi bộ đường dài cả ngày và đã quen với địa hình núi gồ ghề.', 'Hướng dẫn viên địa phương, hỗ trợ thủ tục vào vườn quốc gia, bộ sơ cứu nhóm, chỗ nghỉ trên núi và nước uống.', 'Di chuyển đến Lào Cai, bảo hiểm du lịch cá nhân, các bữa ăn không nêu trong lịch trình và trang bị trekking cá nhân.'),
    ('Săn biển mây Tà Xùa', 'Khám phá sống lưng khủng long trên biển mây, các điểm ngắm cảnh trên đỉnh núi và nghỉ tại homestay địa phương.', 'Có thể đi bộ nhiều giờ trên đường đồi và một số đoạn hẹp.', 'Hướng dẫn viên địa phương, homestay, phổ biến lộ trình, thiết bị an toàn dùng chung và nước uống.', 'Di chuyển đến Tà Xùa, bảo hiểm du lịch, bữa ăn cá nhân và trang bị cá nhân.'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 'Hành trình ba ngày qua rừng trúc và những dãy núi cao để chinh phục đỉnh Bạch Mộc Lương Tử giữa biển mây.', 'Khuyến nghị có thể lực tốt và kinh nghiệm trekking nhiều ngày.', 'Hướng dẫn viên địa phương đủ điều kiện, đội porter, điểm cắm trại, thiết bị an toàn dùng chung và bữa ăn trên cung đường.', 'Di chuyển đến điểm xuất phát, bảo hiểm cá nhân và trang bị trekking cá nhân.'),
    ('Trekking ven vịnh Lan Hạ', 'Cùng hướng dẫn viên khám phá những vịnh nhỏ yên bình và đảo đá vôi, kết hợp các lối đi bộ ven biển và nghỉ đêm gần Cát Bà.', 'Không cần kinh nghiệm trekking; phù hợp với người mới có thể lực cơ bản.', 'Hướng dẫn viên địa phương, chỗ nghỉ qua đêm, phổ biến lộ trình và nước uống.', 'Di chuyển đến Cát Bà, bảo hiểm du lịch và các bữa ăn không nêu trong lịch trình.')
) AS v(title, description, required_experience, included_services, excluded_services)
WHERE t.title = v.title
  AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001');
INSERT INTO tour_locations (tour_id, location_id, visit_order)
SELECT t.id, l.id, 1
FROM tours t
JOIN locations l ON l.name = CASE t.title
    WHEN 'Chinh phục sống núi Fansipan' THEN 'Fansipan'
    WHEN 'Săn biển mây Tà Xùa' THEN 'Tà Xùa'
    WHEN 'Trekking rừng mây Bạch Mộc Lương Tử' THEN 'Bạch Mộc Lương Tử'
    WHEN 'Trekking ven vịnh Lan Hạ' THEN 'Vịnh Lan Hạ'
END
WHERE t.title IN ('Chinh phục sống núi Fansipan', 'Săn biển mây Tà Xùa',
                  'Trekking rừng mây Bạch Mộc Lương Tử', 'Trekking ven vịnh Lan Hạ')
  AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
ON CONFLICT (tour_id, location_id) DO UPDATE SET visit_order = EXCLUDED.visit_order;

INSERT INTO tour_media (tour_id, media_type, url, caption, display_order, is_cover)
SELECT t.id, 'image', v.cover_url, v.caption, 0, TRUE
FROM tours t
JOIN (VALUES
    ('Chinh phục sống núi Fansipan', '/images/demo-fansipan.jpg', 'Sống núi Fansipan trên biển mây'),
    ('Săn biển mây Tà Xùa', '/images/demo-ta-xua.jpg', 'Sống lưng khủng long Tà Xùa'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', '/images/demo-bach-moc.jpg', 'Dãy núi cao giữa biển mây'),
    ('Trekking ven vịnh Lan Hạ', '/images/demo-lan-ha.jpg', 'Đảo đá vôi và lối đi ven biển')
) AS v(title, cover_url, caption) ON v.title = t.title
WHERE t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
ON CONFLICT (tour_id) WHERE is_cover DO UPDATE SET
    url = EXCLUDED.url,
    caption = EXCLUDED.caption,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO tour_activities (tour_id, day_number, display_order, title, description, start_time, location_id)
SELECT t.id, v.day_number, 0, v.title, v.description, v.start_time, l.id
FROM (VALUES
    ('Chinh phục sống núi Fansipan', 1, 'Băng rừng đến điểm nghỉ núi', 'Gặp hướng dẫn viên, kiểm tra trang bị và bắt đầu chặng leo qua rừng Hoàng Liên Sơn.', '08:00'::time, 'Fansipan'),
    ('Chinh phục sống núi Fansipan', 2, 'Đón bình minh trên đỉnh', 'Khởi hành trước bình minh, chinh phục đoạn sống núi cuối cùng rồi xuống điểm xuất phát.', '04:30'::time, 'Fansipan'),
    ('Săn biển mây Tà Xùa', 1, 'Tiến vào sống lưng khủng long', 'Đi từ bản hướng lên sống lưng núi, dừng chân tại những điểm ngắm cảnh an toàn.', '07:30'::time, 'Tà Xùa'),
    ('Săn biển mây Tà Xùa', 2, 'Ngắm biển mây buổi sớm', 'Tản bộ trên sống núi buổi sáng rồi trở về bản sau bữa sáng.', '06:00'::time, 'Tà Xùa'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 1, 'Bắt đầu chặng leo xuyên rừng', 'Cùng hướng dẫn viên và đội porter băng rừng đến điểm cắm trại đầu tiên.', '07:00'::time, 'Bạch Mộc Lương Tử'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 2, 'Lên điểm trại cao', 'Tiếp tục vượt đường cây, nghỉ tại điểm trại cao trước chặng chinh phục đỉnh.', '06:30'::time, 'Bạch Mộc Lương Tử'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 3, 'Chinh phục đỉnh và xuống núi', 'Lên đỉnh từ sáng sớm rồi xuống núi về điểm xuất phát.', '04:30'::time, 'Bạch Mộc Lương Tử'),
    ('Trekking ven vịnh Lan Hạ', 1, 'Lối mòn ven đảo và vịnh nhỏ', 'Gặp hướng dẫn viên địa phương, khám phá những lối đi ven biển nhìn ra vịnh.', '09:00'::time, 'Vịnh Lan Hạ'),
    ('Trekking ven vịnh Lan Hạ', 2, 'Tản bộ ngắm cảnh buổi sáng', 'Tản bộ ngắn buổi sáng đến điểm ngắm đảo đá vôi trước khi trở về Cát Bà.', '08:00'::time, 'Vịnh Lan Hạ')
) AS v(tour_title, day_number, title, description, start_time, location_name)
JOIN tours t ON t.title = v.tour_title
            AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
JOIN locations l ON l.name = v.location_name
ON CONFLICT (tour_id, day_number, display_order) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    start_time = EXCLUDED.start_time,
    location_id = EXCLUDED.location_id;

INSERT INTO tour_equipment_packages (tour_id, title, included_items, shared_items, personal_items_to_bring)
SELECT t.id, 'Bộ trang bị an toàn trekking tiêu chuẩn',
       'Bộ sơ cứu nhóm, dụng cụ định hướng và thiết bị liên lạc khẩn cấp.',
       'Lều trú và dụng cụ nấu ăn dùng chung cho các chặng có cắm trại.',
       'Chuẩn bị giày trekking đã đi quen, quần áo phù hợp thời tiết, bình nước cá nhân và đèn đội đầu.'
FROM tours t
WHERE t.title IN ('Chinh phục sống núi Fansipan', 'Săn biển mây Tà Xùa',
                  'Trekking rừng mây Bạch Mộc Lương Tử', 'Trekking ven vịnh Lan Hạ')
  AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
ON CONFLICT (tour_id) DO UPDATE SET
    title = EXCLUDED.title,
    included_items = EXCLUDED.included_items,
    shared_items = EXCLUDED.shared_items,
    personal_items_to_bring = EXCLUDED.personal_items_to_bring,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO packing_items (tour_id, name, is_required, note, display_order)
SELECT t.id, v.name, v.is_required, v.note, v.display_order
FROM tours t
CROSS JOIN (VALUES
    ('Bình nước', TRUE, 'Mang đủ nước dùng trong ngày.', 0),
    ('Giày trekking đã đi quen', TRUE, 'Chọn giày có độ bám tốt.', 1),
    ('Áo khoác mưa nhẹ', TRUE, 'Thời tiết trên núi có thể thay đổi nhanh.', 2),
    ('Đèn đội đầu', TRUE, 'Mang pin dự phòng khi xuất phát sớm.', 3)
) AS v(name, is_required, note, display_order)
WHERE t.title IN ('Chinh phục sống núi Fansipan', 'Săn biển mây Tà Xùa',
                  'Trekking rừng mây Bạch Mộc Lương Tử', 'Trekking ven vịnh Lan Hạ')
  AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
  AND NOT EXISTS (
      SELECT 1 FROM packing_items p WHERE p.tour_id = t.id AND p.name = v.name
  );

INSERT INTO tour_schedules (tour_id, company_id, starts_at, ends_at, capacity, held_seats,
                            confirmed_seats, base_price_vnd, equipment_fee_vnd, deposit_percent,
                            balance_due_at, meeting_point, status)
SELECT t.id, t.company_id,
       CURRENT_TIMESTAMP + make_interval(days => 35 + v.start_offset_days),
       CURRENT_TIMESTAMP + make_interval(days => 35 + v.start_offset_days + t.duration_days),
       10, 0, 0, v.base_price_vnd, v.equipment_fee_vnd, 30.00,
       CURRENT_TIMESTAMP + make_interval(days => 21 + v.start_offset_days),
       v.meeting_point, 'scheduled'
FROM (VALUES
    ('Chinh phục sống núi Fansipan', 0, 2900000::bigint, 150000::bigint, 'Nhà thờ đá Sa Pa'),
    ('Săn biển mây Tà Xùa', 8, 1850000::bigint, 100000::bigint, 'Quảng trường thị trấn Bắc Yên'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 16, 3650000::bigint, 200000::bigint, 'Bản Sàng Ma Sáo'),
    ('Trekking ven vịnh Lan Hạ', 24, 2450000::bigint, 0::bigint, 'Cầu cảng Cát Bà')
) AS v(tour_title, start_offset_days, base_price_vnd, equipment_fee_vnd, meeting_point)
JOIN tours t ON t.title = v.tour_title
            AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001')
WHERE NOT EXISTS (
    SELECT 1 FROM tour_schedules s
    WHERE s.tour_id = t.id
      AND s.status = 'scheduled'
      AND s.starts_at >= CURRENT_TIMESTAMP
      AND s.starts_at < CURRENT_TIMESTAMP + INTERVAL '90 days'
);

UPDATE tour_schedules s SET meeting_point = v.meeting_point
FROM tours t
JOIN (VALUES
    ('Chinh phục sống núi Fansipan', 'Nhà thờ đá Sa Pa'),
    ('Săn biển mây Tà Xùa', 'Quảng trường thị trấn Bắc Yên'),
    ('Trekking rừng mây Bạch Mộc Lương Tử', 'Bản Sàng Ma Sáo'),
    ('Trekking ven vịnh Lan Hạ', 'Cầu cảng Cát Bà')
) AS v(title, meeting_point) ON v.title = t.title
WHERE s.tour_id = t.id
  AND t.company_id = (SELECT id FROM companies WHERE tax_code = 'DEMO-WETREK-001');

COMMIT;
