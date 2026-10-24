BEGIN;

-- 1. Admin account
INSERT INTO trekking.app_accounts (email, password_hash, full_name, phone, status, email_verified_at)
VALUES ('admin@trekking.vn', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890', 'Admin System', '0901234567', 'active', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    v_admin_id BIGINT;
    v_company1_id BIGINT;
    v_company2_id BIGINT;
    v_policy1_id BIGINT;
    v_policy2_id BIGINT;
    v_pv1_id BIGINT;
    v_pv2_id BIGINT;
    v_tour1_id BIGINT;
    v_tour2_id BIGINT;
BEGIN
    SELECT id INTO v_admin_id FROM trekking.app_accounts WHERE email = 'admin@trekking.vn';

    -- 2. Companies
    INSERT INTO trekking.companies (name, tax_code, representative_name, contact_email, contact_phone, status, approved_by, approved_at)
    VALUES ('Sapa Trekking Adventures', '0109988771', 'Nguyen Van A', 'contact@sapatrekking.com', '0912345678', 'approved', v_admin_id, CURRENT_TIMESTAMP)
    RETURNING id INTO v_company1_id;

    INSERT INTO trekking.companies (name, tax_code, representative_name, contact_email, contact_phone, status, approved_by, approved_at)
    VALUES ('Oxalis Cave Expeditions', '0109988772', 'Tran Thi B', 'contact@oxalis.com', '0987654321', 'approved', v_admin_id, CURRENT_TIMESTAMP)
    RETURNING id INTO v_company2_id;

    -- 3. Policies & Versions
    INSERT INTO trekking.policies (code, title, kind, company_id, created_by, active)
    VALUES ('SAPA_CANCEL_STD', 'Standard Sapa Cancellation Policy', 'cancellation', v_company1_id, v_admin_id, true)
    RETURNING id INTO v_policy1_id;

    INSERT INTO trekking.policy_versions (policy_id, version_number, content, effective_from, approved_by, approved_at)
    VALUES (v_policy1_id, 1, 'Full refund up to 7 days before departure. 50% refund 3-7 days before departure. Non-refundable within 72 hours.', CURRENT_TIMESTAMP, v_admin_id, CURRENT_TIMESTAMP)
    RETURNING id INTO v_pv1_id;

    INSERT INTO trekking.policies (code, title, kind, company_id, created_by, active)
    VALUES ('OXALIS_CANCEL_FLEX', 'Flexible Cave Expedition Policy', 'cancellation', v_company2_id, v_admin_id, true)
    RETURNING id INTO v_policy2_id;

    INSERT INTO trekking.policy_versions (policy_id, version_number, content, effective_from, approved_by, approved_at)
    VALUES (v_policy2_id, 1, '100% refund for weather cancellations or safety hazards. 80% refund up to 14 days before trek.', CURRENT_TIMESTAMP, v_admin_id, CURRENT_TIMESTAMP)
    RETURNING id INTO v_pv2_id;

    -- 4. Tours
    -- Tour 1: Fansipan Peak Conqueror (Hard)
    INSERT INTO trekking.tours (
        company_id, title, description, difficulty, duration_days,
        required_experience, included_services, excluded_services,
        status, cancellation_policy_version_id, reviewed_by, reviewed_at
    ) VALUES (
        v_company1_id,
        'Fansipan 3-Day Peak Conqueror Trek',
        'Conquer Indochina''s highest peak standing at 3,143 meters above sea level. Trek through dense bamboo forests, steep alpine ridges, and rocky slopes with experienced local Hmong guides. Camp at the 2,800m station under the mountain stars. Spectacular sunrise view over the Hoang Lien Son range.',
        'hard',
        3,
        'Prior hiking experience and good physical endurance required.',
        'Professional mountain guide, porters, national park entrance permit, 2 nights mountain hut/tent accommodation, all meals, sleeping bag.',
        'Personal trekking gear (boots, waterproof jacket), tips, travel insurance.',
        'published',
        v_pv1_id,
        v_admin_id,
        CURRENT_TIMESTAMP
    ) RETURNING id INTO v_tour1_id;

    -- Tour 2: Hang En Cave (Moderate/Beginner friendly)
    INSERT INTO trekking.tours (
        company_id, title, description, difficulty, duration_days,
        required_experience, included_services, excluded_services,
        status, cancellation_policy_version_id, reviewed_by, reviewed_at
    ) VALUES (
        v_company2_id,
        'Hang En Cave 2-Day Jungle & Cave Expedition',
        'Explore the world''s third-largest cave located in Phong Nha-Ke Bang National Park. Features an underground campsite on a pristine sandy beach next to a natural turquoise lake. Easy to moderate jungle hiking suitable for beginners, nature enthusiasts, and adventure lovers.',
        'moderate',
        2,
        'No prior caving experience needed. Suitable for active beginners.',
        'Safety equipment (helmets, caving headlamps, life jackets), campsite tents, all hot meals prepared by local cooks, English-speaking cave expert guide.',
        'Personal items, hiking shoes.',
        'published',
        v_pv2_id,
        v_admin_id,
        CURRENT_TIMESTAMP
    ) RETURNING id INTO v_tour2_id;

    -- 5. Schedules
    -- Fansipan Schedule
    INSERT INTO trekking.tour_schedules (
        tour_id, company_id, starts_at, ends_at, capacity,
        base_price_vnd, equipment_fee_vnd, deposit_percent,
        balance_due_at, meeting_point, contact_phone, status
    ) VALUES (
        v_tour1_id, v_company1_id,
        CURRENT_TIMESTAMP + INTERVAL '10 days',
        CURRENT_TIMESTAMP + INTERVAL '13 days',
        12,
        3850000,
        0,
        30.00,
        CURRENT_TIMESTAMP + INTERVAL '7 days',
        'Sapa Town Plaza, Lao Cai',
        '0912345678',
        'scheduled'
    );

    -- Hang En Schedule
    INSERT INTO trekking.tour_schedules (
        tour_id, company_id, starts_at, ends_at, capacity,
        base_price_vnd, equipment_fee_vnd, deposit_percent,
        balance_due_at, meeting_point, contact_phone, status
    ) VALUES (
        v_tour2_id, v_company2_id,
        CURRENT_TIMESTAMP + INTERVAL '15 days',
        CURRENT_TIMESTAMP + INTERVAL '17 days',
        16,
        7500000,
        0,
        50.00,
        CURRENT_TIMESTAMP + INTERVAL '10 days',
        'Oxalis Headquarters, Phong Nha, Quang Binh',
        '0987654321',
        'scheduled'
    );
END $$;

COMMIT;
