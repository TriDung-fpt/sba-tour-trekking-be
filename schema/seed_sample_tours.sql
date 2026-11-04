BEGIN;

-- 1. Create an admin/operator account if not exists
INSERT INTO trekking.app_accounts (email, password_hash, full_name, phone, status, email_verified_at)
VALUES ('admin@trekking.vn', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Admin System', '0901234567', 'active', CURRENT_TIMESTAMP)
ON CONFLICT (email) DO NOTHING;

DO $$
DECLARE
    v_admin_id BIGINT;
    v_policy1_id BIGINT;
    v_policy2_id BIGINT;
    v_pv1_id BIGINT;
    v_pv2_id BIGINT;
    v_loc_fansipan_id BIGINT;
    v_loc_sapa_id BIGINT;
    v_loc_hang_en_id BIGINT;
    v_loc_phong_nha_id BIGINT;
    v_loc_ta_xua_id BIGINT;
    v_tour1_id BIGINT;
    v_tour2_id BIGINT;
    v_tour3_id BIGINT;
BEGIN
    SELECT id INTO v_admin_id FROM trekking.app_accounts WHERE email = 'admin@trekking.vn';

    -- 2. Policies & Policy Versions
    INSERT INTO trekking.policies (code, title, kind, created_by, active)
    VALUES ('POL_CANCEL_STD', 'Chính sách hoàn hủy tiêu chuẩn', 'cancellation', v_admin_id, true)
    RETURNING id INTO v_policy1_id;

    INSERT INTO trekking.policy_versions (policy_id, version_number, content, effective_from, approved_by, approved_at)
    VALUES (
        v_policy1_id, 1,
        'Hoàn tiền 100% khi hủy trước ngày khởi hành 7 ngày. Hoàn 50% khi hủy từ 3-7 ngày trước khởi hành. Không hoàn tiền nếu hủy trong vòng 72 giờ.',
        CURRENT_TIMESTAMP, v_admin_id, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_pv1_id;

    INSERT INTO trekking.policies (code, title, kind, created_by, active)
    VALUES ('POL_CANCEL_EXP', 'Chính sách hoàn hủy tour thám hiểm hang động', 'cancellation', v_admin_id, true)
    RETURNING id INTO v_policy2_id;

    INSERT INTO trekking.policy_versions (policy_id, version_number, content, effective_from, approved_by, approved_at)
    VALUES (
        v_policy2_id, 1,
        'Hoàn tiền 100% trong trường hợp thời tiết nguy hiểm, bão lũ hoặc cảnh báo an toàn từ ban quản lý vườn quốc gia. Hoàn 80% nếu hủy trước 14 ngày.',
        CURRENT_TIMESTAMP, v_admin_id, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_pv2_id;

    -- 3. Locations
    INSERT INTO trekking.locations (name, province_name, latitude, longitude)
    VALUES ('Đỉnh Fansipan', 'Lào Cai', 22.3033, 103.7753)
    RETURNING id INTO v_loc_fansipan_id;

    INSERT INTO trekking.locations (name, province_name, latitude, longitude)
    VALUES ('Trạm Tôn', 'Lào Cai', 22.3512, 103.7712)
    RETURNING id INTO v_loc_sapa_id;

    INSERT INTO trekking.locations (name, province_name, latitude, longitude)
    VALUES ('Hang Én', 'Quảng Bình', 17.4328, 106.2974)
    RETURNING id INTO v_loc_hang_en_id;

    INSERT INTO trekking.locations (name, province_name, latitude, longitude)
    VALUES ('Vườn Quốc Gia Phong Nha - Kẻ Bàng', 'Quảng Bình', 17.5900, 106.2833)
    RETURNING id INTO v_loc_phong_nha_id;

    INSERT INTO trekking.locations (name, province_name, latitude, longitude)
    VALUES ('Sống lưng khủng long Tà Xùa', 'Sơn La', 21.2833, 104.3167)
    RETURNING id INTO v_loc_ta_xua_id;

    -- 4. Tour 1: Fansipan Peak Trek
    INSERT INTO trekking.tours (
        title, description, difficulty, duration_days,
        required_experience, included_services, excluded_services,
        status, cancellation_policy_version_id, reviewed_by, reviewed_at
    ) VALUES (
        'Tour Trekking Chinh Phục Đỉnh Fansipan 3N2Đ',
        'Chinh phục nóc nhà Đông Dương ở độ cao 3.143m. Hành trình băng qua rừng trúc lùn, rừng đỗ quyên cổ thụ, vượt qua dốc đá hiểm trở cùng hướng dẫn viên bản địa người H''Mông. Nghỉ đêm tại lán trại 2.800m ngắm biển mây và đón bình minh hùng vĩ trên dãy Hoàng Liên Sơn.',
        'hard', 3,
        'Yêu cầu thể lực tốt, có kinh nghiệm trekking hoặc chạy bộ trước chuyến đi.',
        'Hướng dẫn viên chuyên nghiệp, porter mang vác thực phẩm, vé tham quan và giấy phép vườn quốc gia, bảo hiểm du lịch, lều trại túi ngủ, các bữa ăn nóng suốt hành trình.',
        'Đồ dùng cá nhân (giày leo núi chuyên dụng, áo gió chống nước, gậy trekking), tiền tip cho HDV/porter.',
        'published', v_pv1_id, v_admin_id, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_tour1_id;

    -- Tour 1 Locations
    INSERT INTO trekking.tour_locations (tour_id, location_id, visit_order)
    VALUES (v_tour1_id, v_loc_sapa_id, 1), (v_tour1_id, v_loc_fansipan_id, 2);

    -- Tour 1 Equipment Package
    INSERT INTO trekking.tour_equipment_packages (tour_id, title, included_items, shared_items, personal_items_to_bring)
    VALUES (
        v_tour1_id,
        'Gói trang bị leo núi Fansipan',
        'Túi ngủ dã ngoại cách nhiệt, đệm lót chuyên dụng, bát đũa cá nhân, găng tay gai bảo hộ.',
        'Lều trại 4 mùa chống gió bão, bình lọc nước mini, bộ sơ cứu y tế leo núi.',
        'Giày leo núi có độ bám cao, 2-3 bộ đồ nhanh khô, áo ấm lông vũ, áo mưa bộ, đèn pin đội đầu.'
    );

    -- Tour 1 Schedule
    INSERT INTO trekking.tour_schedules (
        tour_id, starts_at, ends_at, capacity,
        base_price_vnd, equipment_fee_vnd, deposit_percent,
        balance_due_at, meeting_point, contact_phone, status
    ) VALUES (
        v_tour1_id,
        CURRENT_TIMESTAMP + INTERVAL '10 days',
        CURRENT_TIMESTAMP + INTERVAL '13 days',
        14, 3850000, 200000, 30.00,
        CURRENT_TIMESTAMP + INTERVAL '7 days',
        'Quảng trường Thị xã Sa Pa, Lào Cai', '0912345678', 'scheduled'
    );

    -- 5. Tour 2: Hang Én Expedition
    INSERT INTO trekking.tours (
        title, description, difficulty, duration_days,
        required_experience, included_services, excluded_services,
        status, cancellation_policy_version_id, reviewed_by, reviewed_at
    ) VALUES (
        'Thám Hiểm Hang Én - Hang Động Tự Nhiên Lớn Thứ 3 Thế Giới (2N1Đ)',
        'Khám phá Hang Én tuyệt mỹ ẩn sâu trong lõi Vườn Quốc Gia Phong Nha - Kẻ Bàng. Trải nghiệm cắm trại bên bờ cát trắng mịn cạnh hồ nước ngọc bích ngầm trong lòng hang khổng lồ. Trekking qua những cánh rừng nguyên sinh xanh mướt và bản Đoòng mộc mạc của đồng bào Bru-Vân Kiều.',
        'moderate', 2,
        'Không yêu cầu kỹ thuật leo núi phức tạp. Phù hợp cho người mới bắt đầu yêu thích thiên nhiên.',
        'Trang thiết bị an toàn tiêu chuẩn quốc tế (nón bảo hiểm Petzl, đèn thám hiểm chuyên dụng, áo phao), lều cắm trại cao cấp, toàn bộ bữa ăn nóng do đầu bếp phục vụ, chuyên gia hang động dẫn đoàn.',
        'Phương tiện di chuyển đến Phong Nha, trang phục cá nhân, giày đi nước bám đá.',
        'published', v_pv2_id, v_admin_id, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_tour2_id;

    -- Tour 2 Locations
    INSERT INTO trekking.tour_locations (tour_id, location_id, visit_order)
    VALUES (v_tour2_id, v_loc_phong_nha_id, 1), (v_tour2_id, v_loc_hang_en_id, 2);

    -- Tour 2 Equipment Package
    INSERT INTO trekking.tour_equipment_packages (tour_id, title, included_items, shared_items, personal_items_to_bring)
    VALUES (
        v_tour2_id,
        'Gói trang bị thám hiểm hang động',
        'Mũ bảo hộ có gắn đèn chuyên dụng, túi chống nước 10L, găng tay thám hiểm.',
        'Thuyền phao trung chuyển qua suối, máy phát điện sạc thiết bị tại bãi trại, bộ lọc nước tiêu chuẩn.',
        'Giày lội suối có rãnh bám, quần áo dài tay chống vắt, tất dày chống ma sát.'
    );

    -- Tour 2 Schedule
    INSERT INTO trekking.tour_schedules (
        tour_id, starts_at, ends_at, capacity,
        base_price_vnd, equipment_fee_vnd, deposit_percent,
        balance_due_at, meeting_point, contact_phone, status
    ) VALUES (
        v_tour2_id,
        CURRENT_TIMESTAMP + INTERVAL '15 days',
        CURRENT_TIMESTAMP + INTERVAL '17 days',
        16, 7500000, 0, 50.00,
        CURRENT_TIMESTAMP + INTERVAL '10 days',
        'Văn phòng tiếp đón Phong Nha, Bố Trạch, Quảng Bình', '0987654321', 'scheduled'
    );

    -- 6. Tour 3: Tà Xùa Cloud Hunting Trek
    INSERT INTO trekking.tours (
        title, description, difficulty, duration_days,
        required_experience, included_services, excluded_services,
        status, cancellation_policy_version_id, reviewed_by, reviewed_at
    ) VALUES (
        'Trekking Săn Mây Tà Xùa - Chinh Phục Sống Lưng Khủng Long (2N1Đ)',
        'Khám phá thiên đường săn mây Tà Xùa ở độ cao 2.865m. Chinh phục con đường mòn sống lưng khủng long cheo leo giữa bồng bềnh mây trắng. Chiêm ngưỡng rừng rêu ma mị cổ tích và văn hóa bản địa độc đáo của người Mông.',
        'moderate', 2,
        'Phù hợp với người có sức khỏe tốt, không sợ độ cao.',
        'Xe đưa đón Hà Nội - Tà Xùa khứ hồi, hướng dẫn viên địa phương, ăn uống các bữa theo chương trình, homestay/lán nghỉ, bảo hiểm.',
        'Chi phí cá nhân, đồ uống ngoài menu.',
        'published', v_pv1_id, v_admin_id, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_tour3_id;

    -- Tour 3 Locations
    INSERT INTO trekking.tour_locations (tour_id, location_id, visit_order)
    VALUES (v_tour3_id, v_loc_ta_xua_id, 1);

    -- Tour 3 Equipment Package
    INSERT INTO trekking.tour_equipment_packages (tour_id, title, included_items, shared_items, personal_items_to_bring)
    VALUES (
        v_tour3_id,
        'Gói trekking Tà Xùa',
        'Gậy trekking trợ lực, áo mưa tiện lợi, nước khoáng bổ sung điện giải.',
        'Bộ đàm liên lạc, túi sơ cứu y tế.',
        'Giày thể thao đế bám, áo khoác cản gió, pin sạc dự phòng, kem chống muỗi/côn trùng.'
    );

    -- Tour 3 Schedule
    INSERT INTO trekking.tour_schedules (
        tour_id, starts_at, ends_at, capacity,
        base_price_vnd, equipment_fee_vnd, deposit_percent,
        balance_due_at, meeting_point, contact_phone, status
    ) VALUES (
        v_tour3_id,
        CURRENT_TIMESTAMP + INTERVAL '5 days',
        CURRENT_TIMESTAMP + INTERVAL '7 days',
        20, 2450000, 0, 30.00,
        CURRENT_TIMESTAMP + INTERVAL '3 days',
        'Cổng chào Thị trấn Bắc Yên, Sơn La', '0933456789', 'scheduled'
    );

END $$;

COMMIT;
