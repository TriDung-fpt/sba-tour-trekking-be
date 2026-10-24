-- 39 tables; 2 views; PostgreSQL DDL, khong phai full backend.
-- TREKKING MARKETPLACE - PostgreSQL 16+ - baseline DDL v0.1
-- Kiem thu: PostgreSQL 18.3 qua PGlite 0.5.8; tao schema thanh cong va 24 checks dat.
-- Dung cu phap PostgreSQL 16+; chua chay truc tiep tren server PostgreSQL 16.
-- 39 bang gom ca bang noi/lich su; khong phai 39 entity conceptual moi.
-- Ngay: 2026-10-07. Nguon: PRD V2 va conceptual dang thao luan.
-- Chay MOT LAN trong database moi/da tao: psql -v ON_ERROR_STOP=1 -d trekking_db -f trekking_postgresql_v0_1.sql
-- pgAdmin/DBeaver: ket noi database, mo file va Execute script.
-- Khong CREATE DATABASE, khong DROP, khong xoa du lieu. Neu schema trekking da co,
-- script se bao loi va rollback; khong dung IF NOT EXISTS de che schema khac nhau.
-- Sau khi da dung, thay doi bang migration ALTER TABLE moi, khong sua lai baseline.
-- Ten cot snake_case; ID BIGINT IDENTITY; tien BIGINT don vi VND; thoi gian TIMESTAMPTZ.
-- Tai khoan dung chung app_accounts; Customer/Admin/Staff la vai tro, khong tao 4 bang login.
-- Moi Guide profile thuoc mot Company; bang schedule_guides CHO PHEP nhieu Guide/chuyen.
-- Luu nhieu lan hoan/chi de truy vet: khong dong nghia da chot nghiep vu chia nho tien.
-- Quy tac so Guide va so lan hoan/chi can xac nhan voi client truoc khi mo tinh nang.
-- Policy co version rieng de luu dung dieu khoan luc dat/cham nhan.
-- Khong quan ly kho, thue le, giao nhan hoac coc do. Lua chon dung cu chung cho Booking.
-- Hop dong/UI: ten hien thi tieng Viet; cac status tieng Anh chi la gia tri luu tru.
--
-- RANH GIOI DDL / BACKEND (BAT BUOC DOC TRUOC KHI LAM API):
-- 1. Backend kiem quyen tren tung Company/Booking/Schedule, khong tin ID client gui.
-- 2. Giu/nhả/confirm cho: khoa dong tour_schedules FOR UPDATE trong cung transaction,
--    cap nhat held_seats/confirmed_seats va Booking; chi xu ly mot lan. CHECK capacity
--    chi chan counter vuot capacity, KHONG tu cong so nguoi tu tat ca Booking.
-- 3. participant_count phai khop participants; gia va snapshot tinh o server.
-- 4. Chi insert customer_payments khi webhook da duoc xac minh hoac ke toan da doi chieu.
--    Khong nham payment_attempt thanh cong tren trinh duyet voi tien da nhan.
-- 5. Hoan tien: khoa Booking; tru ca so da hoan va so dang cam ket hoan de chong over-refund.
--    DDL chua tu kiem tong tien qua nhieu dong; kiem trong transaction backend.
-- 6. Doi soat: tinh tren doanh thu giu lai; phi 10% tinh ca dung cu; phi cong nen tang chiu.
--    Khoa/kiem lai hoan va tien giu truoc duyet/chi; du 72h tu actual_completed_at,
--    thu du va dung ky tuan. Khong tu chi ngan hang. Khong sua so da chi; tao dong dieu chinh.
-- 7. Co chuyen trang thai, xac thuc ma email, idempotency, thu hoi session va audit o backend.
--    Bang auth session/token, vector embeddings va bao gia se la migration sau.
-- 8. DDL khong phai payment engine da san sang van hanh. Xay/test transaction API truoc tien that.
-- 9. Quy tac hoan theo moc thoi gian nam trong policy_versions.content; chua tu dat ty le.
--    App can luu va danh gia rule da duyet; doi tac huy => hoan 100% tien tour da thu.
-- 10. API phai validate JSON snapshots; CHECK json chi kiem kieu object, khong kiem noi dung.
--
BEGIN;
CREATE SCHEMA trekking;
SET LOCAL search_path TO trekking, public;

-- Tài khoản đăng nhập dùng chung; chỉ lưu mật khẩu đã băm.
CREATE TABLE app_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(254) NOT NULL CHECK (btrim(email) <> ''),
    password_hash TEXT NOT NULL CHECK (btrim(password_hash) <> ''),
    full_name VARCHAR(200) NOT NULL CHECK (btrim(full_name) <> ''),
    phone VARCHAR(30),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active','blocked','disabled')),
    email_verified_at TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    blocked_reason TEXT,
    blocked_by BIGINT REFERENCES app_accounts(id),
    CHECK (status <> 'blocked' OR NULLIF(btrim(blocked_reason),'') IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE app_accounts IS 'Tài khoản đăng nhập dùng chung; chỉ lưu mật khẩu đã băm.';
CREATE UNIQUE INDEX uq_accounts_email ON app_accounts (lower(btrim(email)));

-- Vai trò cố định trong code; một tài khoản có thể kiêm nhiều vai trò.
CREATE TABLE account_roles (
    account_id BIGINT NOT NULL REFERENCES app_accounts(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('customer','company_manager','guide','support_staff','content_staff','accountant','admin')),
    PRIMARY KEY (account_id, role),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE account_roles IS 'Vai trò cố định trong code; một tài khoản có thể kiêm nhiều vai trò.';

-- Đối tác cung cấp tour; tài khoản quản lý nối qua company_members.
CREATE TABLE companies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(250) NOT NULL CHECK (btrim(name) <> ''),
    tax_code VARCHAR(50),
    license_file_url TEXT,
    representative_name VARCHAR(200),
    contact_email VARCHAR(254),
    contact_phone VARCHAR(30),
    address TEXT,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','approved','suspended','rejected')),
    approved_by BIGINT REFERENCES app_accounts(id),
    approved_at TIMESTAMPTZ,
    decision_reason TEXT,
    CHECK (status <> 'approved' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE companies IS 'Đối tác cung cấp tour; tài khoản quản lý nối qua company_members.';
CREATE UNIQUE INDEX uq_company_tax_code ON companies(tax_code) WHERE tax_code IS NOT NULL;

-- Liên kết tài khoản với đối tác để xác định phạm vi quản lý.
CREATE TABLE company_members (
    company_id BIGINT NOT NULL REFERENCES companies(id),
    account_id BIGINT NOT NULL REFERENCES app_accounts(id),
    member_role TEXT NOT NULL DEFAULT 'manager' CHECK (member_role IN ('owner','manager')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (company_id, account_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE company_members IS 'Liên kết tài khoản với đối tác để xác định phạm vi quản lý.';

-- Hồ sơ hướng dẫn viên của một đối tác.
CREATE TABLE guides (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    account_id BIGINT NOT NULL UNIQUE REFERENCES app_accounts(id),
    bio TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (id, company_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE guides IS 'Hồ sơ hướng dẫn viên của một đối tác.';

-- Danh tính chính sách; nội dung từng bản nằm trong policy_versions.
CREATE TABLE policies (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(250) NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('cooperation','cancellation','payment','general')),
    company_id BIGINT REFERENCES companies(id),
    created_by BIGINT NOT NULL REFERENCES app_accounts(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE policies IS 'Danh tính chính sách; nội dung từng bản nằm trong policy_versions.';

-- Nội dung điều khoản có phiên bản; bản đã được tham chiếu được khóa bằng trigger.
CREATE TABLE policy_versions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_id BIGINT NOT NULL REFERENCES policies(id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    content TEXT NOT NULL CHECK (btrim(content) <> ''),
    effective_from TIMESTAMPTZ NOT NULL,
    approved_by BIGINT REFERENCES app_accounts(id),
    approved_at TIMESTAMPTZ,
    UNIQUE (policy_id, version_number),
    CHECK ((approved_by IS NULL) = (approved_at IS NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE policy_versions IS 'Nội dung điều khoản có phiên bản; bản đã được tham chiếu được khóa bằng trigger.';

-- Bằng chứng đối tác chấp nhận một phiên bản điều khoản hợp tác.
CREATE TABLE company_agreements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    policy_version_id BIGINT NOT NULL REFERENCES policy_versions(id),
    accepted_by BIGINT NOT NULL REFERENCES app_accounts(id),
    accepted_at TIMESTAMPTZ NOT NULL,
    accepted_ip INET,
    email_verified_at TIMESTAMPTZ NOT NULL,
    platform_fee_percent NUMERIC(5,2) NOT NULL DEFAULT 10.00 CHECK (platform_fee_percent BETWEEN 0 AND 100),
    payout_wait_hours INTEGER NOT NULL DEFAULT 72 CHECK (payout_wait_hours >= 0),
    payout_terms_snapshot TEXT NOT NULL,
    UNIQUE (company_id, policy_version_id),
    UNIQUE (id, company_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE company_agreements IS 'Bằng chứng đối tác chấp nhận một phiên bản điều khoản hợp tác.';

-- Địa điểm dùng để phân loại/tìm kiếm tour.
CREATE TABLE locations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    province_name VARCHAR(150),
    latitude NUMERIC(9,6) CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9,6) CHECK (longitude BETWEEN -180 AND 180),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE locations IS 'Địa điểm dùng để phân loại/tìm kiếm tour.';

-- Sản phẩm tour; giá và số chỗ nằm ở từng lịch tổ chức.
CREATE TABLE tours (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    title VARCHAR(250) NOT NULL CHECK (btrim(title) <> ''),
    description TEXT,
    difficulty TEXT CHECK (difficulty IN ('easy','moderate','hard','expert')),
    duration_days INTEGER CHECK (duration_days > 0),
    required_experience TEXT,
    included_services TEXT,
    excluded_services TEXT,
    route_file_url TEXT,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','pending','published','rejected','archived')),
    cancellation_policy_version_id BIGINT REFERENCES policy_versions(id),
    reviewed_by BIGINT REFERENCES app_accounts(id),
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    UNIQUE (id, company_id),
    CHECK (status <> 'published' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND cancellation_policy_version_id IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tours IS 'Sản phẩm tour; giá và số chỗ nằm ở từng lịch tổ chức.';

-- Các địa điểm tour đi đến.
CREATE TABLE tour_locations (
    tour_id BIGINT NOT NULL REFERENCES tours(id) ON DELETE CASCADE,
    location_id BIGINT NOT NULL REFERENCES locations(id),
    visit_order INTEGER NOT NULL DEFAULT 1 CHECK (visit_order > 0),
    PRIMARY KEY (tour_id, location_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_locations IS 'Các địa điểm tour đi đến.';

-- Ảnh/video giới thiệu tour; URL tới nơi lưu file.
CREATE TABLE tour_media (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES tours(id) ON DELETE CASCADE,
    media_type TEXT NOT NULL CHECK (media_type IN ('image','video')),
    url TEXT NOT NULL,
    caption TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_cover BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_media IS 'Ảnh/video giới thiệu tour; URL tới nơi lưu file.';
CREATE UNIQUE INDEX uq_tour_cover ON tour_media(tour_id) WHERE is_cover;

-- Hoạt động trong lịch trình mẫu của tour.
CREATE TABLE tour_activities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES tours(id) ON DELETE CASCADE,
    day_number INTEGER NOT NULL CHECK (day_number > 0),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    title VARCHAR(250) NOT NULL,
    description TEXT,
    start_time TIME,
    location_id BIGINT REFERENCES locations(id),
    UNIQUE (tour_id, day_number, display_order),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_activities IS 'Hoạt động trong lịch trình mẫu của tour.';

-- Một gói dụng cụ mô tả cho mỗi tour; không phải kho hoặc mặt hàng thuê.
CREATE TABLE tour_equipment_packages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL UNIQUE REFERENCES tours(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    included_items TEXT NOT NULL,
    shared_items TEXT,
    personal_items_to_bring TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_equipment_packages IS 'Một gói dụng cụ mô tả cho mỗi tour; không phải kho hoặc mặt hàng thuê.';

-- Đồ cần chuẩn bị; không có tồn kho.
CREATE TABLE tour_required_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES tours(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    note TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_required_items IS 'Đồ khách cần chuẩn bị; không quản lý tồn kho.';

-- Chuyến cụ thể; backend cập nhật counter chỗ trong transaction có khóa.
CREATE TABLE tour_schedules (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    held_seats INTEGER NOT NULL DEFAULT 0 CHECK (held_seats >= 0),
    confirmed_seats INTEGER NOT NULL DEFAULT 0 CHECK (confirmed_seats >= 0),
    base_price_vnd BIGINT NOT NULL CHECK (base_price_vnd BETWEEN 0 AND 1000000000000),
    equipment_fee_vnd BIGINT NOT NULL DEFAULT 0 CHECK (equipment_fee_vnd BETWEEN 0 AND 1000000000000),
    deposit_percent NUMERIC(5,2) NOT NULL CHECK (deposit_percent > 0 AND deposit_percent <= 100),
    balance_due_at TIMESTAMPTZ NOT NULL,
    meeting_point TEXT,
    contact_phone VARCHAR(30),
    status TEXT NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled','ongoing','completed','cancelled')),
    actual_completed_at TIMESTAMPTZ,
    completed_by BIGINT REFERENCES app_accounts(id),
    cancellation_reason TEXT,
    lock_version INTEGER NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    FOREIGN KEY (tour_id, company_id) REFERENCES tours(id, company_id),
    UNIQUE (id, company_id),
    CHECK (ends_at > starts_at),
    CHECK (balance_due_at <= starts_at),
    CHECK (held_seats::BIGINT + confirmed_seats <= capacity),
    CHECK (status <> 'completed' OR (actual_completed_at IS NOT NULL AND completed_by IS NOT NULL)),
    CHECK (status <> 'cancelled' OR NULLIF(btrim(cancellation_reason),'') IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_schedules IS 'Chuyến cụ thể; backend cập nhật counter chỗ trong transaction có khóa.';

-- Phân công Guide đúng đối tác; nhiều Guide/chuyến là khả năng lưu, cần chốt UI.
CREATE TABLE schedule_guides (
    schedule_id BIGINT NOT NULL,
    guide_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    is_lead BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (schedule_id, company_id) REFERENCES tour_schedules(id, company_id),
    FOREIGN KEY (guide_id, company_id) REFERENCES guides(id, company_id),
    PRIMARY KEY (schedule_id, guide_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE schedule_guides IS 'Phân công Guide đúng đối tác; nhiều Guide/chuyến là khả năng lưu, cần chốt UI.';
CREATE UNIQUE INDEX uq_lead_guide ON schedule_guides(schedule_id) WHERE is_lead;

-- Một lựa chọn đang lưu cho một tài khoản; không giữ chỗ.
CREATE TABLE carts (
    customer_id BIGINT PRIMARY KEY REFERENCES app_accounts(id) ON DELETE CASCADE,
    schedule_id BIGINT NOT NULL REFERENCES tour_schedules(id),
    participant_count INTEGER NOT NULL CHECK (participant_count > 0),
    includes_equipment BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE carts IS 'Một lựa chọn đang lưu cho một tài khoản; không giữ chỗ.';

-- Một đơn/một lịch/một đối tác; giá và chính sách được chụp lại lúc đặt.
CREATE TABLE bookings (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_code VARCHAR(40) NOT NULL UNIQUE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL REFERENCES app_accounts(id),
    schedule_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    participant_count INTEGER NOT NULL CHECK (participant_count BETWEEN 1 AND 1000000),
    includes_equipment BOOLEAN NOT NULL,
    base_price_vnd BIGINT NOT NULL CHECK (base_price_vnd BETWEEN 0 AND 1000000000000),
    equipment_fee_vnd BIGINT NOT NULL CHECK (equipment_fee_vnd BETWEEN 0 AND 1000000000000),
    total_price_vnd BIGINT GENERATED ALWAYS AS
      (participant_count::BIGINT * (base_price_vnd + equipment_fee_vnd)) STORED,
    deposit_percent NUMERIC(5,2) NOT NULL CHECK (deposit_percent > 0 AND deposit_percent <= 100),
    platform_fee_percent NUMERIC(5,2) NOT NULL DEFAULT 10.00 CHECK (platform_fee_percent BETWEEN 0 AND 100),
    cancellation_policy_version_id BIGINT NOT NULL REFERENCES policy_versions(id),
    company_agreement_id BIGINT,
    policy_snapshot JSONB NOT NULL CHECK (jsonb_typeof(policy_snapshot) = 'object'),
    equipment_snapshot JSONB NOT NULL DEFAULT '{}'::JSONB CHECK (jsonb_typeof(equipment_snapshot) = 'object'),
    status TEXT NOT NULL DEFAULT 'pending_payment' CHECK (status IN ('pending_payment','confirmed','expired','cancelled','completed','no_show')),
    hold_expires_at TIMESTAMPTZ NOT NULL,
    balance_due_at TIMESTAMPTZ NOT NULL,
    price_locked_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancellation_reason TEXT,
    cancelled_by BIGINT REFERENCES app_accounts(id),
    lock_version INTEGER NOT NULL DEFAULT 0 CHECK (lock_version >= 0),
    FOREIGN KEY (schedule_id, company_id) REFERENCES tour_schedules(id, company_id),
    FOREIGN KEY (company_agreement_id, company_id) REFERENCES company_agreements(id, company_id),
    UNIQUE (id, company_id),
    UNIQUE (id, customer_id),
    CHECK (includes_equipment OR equipment_fee_vnd = 0),
    CHECK (status <> 'cancelled' OR (cancelled_at IS NOT NULL AND NULLIF(btrim(cancellation_reason),'') IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE bookings IS 'Một đơn/một lịch/một đối tác; giá và chính sách được chụp lại lúc đặt.';

-- Người trong một đơn; account_id tùy chọn, không tự coi người đầu là chủ đơn.
CREATE TABLE participants (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    account_id BIGINT REFERENCES app_accounts(id),
    full_name VARCHAR(200) NOT NULL CHECK (btrim(full_name) <> ''),
    phone VARCHAR(30),
    email VARCHAR(254),
    checked_in_at TIMESTAMPTZ,
    checked_in_by BIGINT REFERENCES app_accounts(id),
    check_in_note TEXT,
    UNIQUE (booking_id, account_id),
    UNIQUE (id, booking_id, account_id),
    CHECK ((checked_in_at IS NULL) = (checked_in_by IS NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE participants IS 'Người trong một đơn; account_id tùy chọn, không tự coi người đầu là chủ đơn.';

-- Lần thử thanh toán; không phải khoản thu thực tế.
CREATE TABLE payment_attempts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    provider TEXT NOT NULL CHECK (provider IN ('payos','vnpay','manual')),
    merchant_reference VARCHAR(150) NOT NULL,
    purpose TEXT NOT NULL CHECK (purpose IN ('deposit','balance','full')),
    requested_amount_vnd BIGINT NOT NULL CHECK (requested_amount_vnd > 0),
    status TEXT NOT NULL DEFAULT 'created' CHECK (status IN ('created','pending','succeeded','failed','expired')),
    expires_at TIMESTAMPTZ,
    UNIQUE (provider, merchant_reference),
    UNIQUE (id, booking_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE payment_attempts IS 'Lần thử thanh toán; không phải khoản thu thực tế.';

-- Thông báo cổng; chỉ lưu dữ liệu đã loại thông tin nhạy cảm.
CREATE TABLE payment_notices (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id BIGINT REFERENCES payment_attempts(id),
    provider TEXT NOT NULL CHECK (provider IN ('payos','vnpay')),
    event_key VARCHAR(200) NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    processing_error TEXT,
    UNIQUE (provider, event_key),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE payment_notices IS 'Thông báo cổng; chỉ lưu dữ liệu đã loại thông tin nhạy cảm.';

-- Khoản tiền thực đã nhận; sai lệch chưa được tính doanh thu tour.
CREATE TABLE customer_payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    attempt_id BIGINT UNIQUE,
    provider TEXT NOT NULL CHECK (provider IN ('payos','vnpay','manual')),
    transaction_reference VARCHAR(200) NOT NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    gateway_fee_vnd BIGINT NOT NULL DEFAULT 0 CHECK (gateway_fee_vnd >= 0),
    allocation_status TEXT NOT NULL DEFAULT 'pending_review' CHECK (allocation_status IN ('pending_review','accepted')),
    received_at TIMESTAMPTZ NOT NULL,
    verified_by BIGINT REFERENCES app_accounts(id),
    evidence_url TEXT,
    FOREIGN KEY (attempt_id, booking_id) REFERENCES payment_attempts(id, booking_id),
    UNIQUE (provider, transaction_reference),
    UNIQUE (id, booking_id),
    CHECK (provider <> 'manual' OR verified_by IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customer_payments IS 'Khoản tiền thực đã nhận; sai lệch chưa được tính doanh thu tour.';

-- Yêu cầu hoàn theo đơn; số tiền do backend tính từ chính sách đã lưu.
CREATE TABLE refund_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    requested_by BIGINT REFERENCES app_accounts(id),
    reason TEXT NOT NULL,
    requested_amount_vnd BIGINT NOT NULL CHECK (requested_amount_vnd > 0),
    approved_amount_vnd BIGINT CHECK (approved_amount_vnd > 0 AND approved_amount_vnd <= requested_amount_vnd),
    status TEXT NOT NULL DEFAULT 'requested' CHECK (status IN ('requested','approved','processing','completed','failed','rejected')),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    approved_by BIGINT REFERENCES app_accounts(id),
    approved_at TIMESTAMPTZ,
    decision_note TEXT,
    UNIQUE (id, booking_id),
    CHECK (status NOT IN ('approved','processing','completed','failed') OR
      (approved_amount_vnd IS NOT NULL AND approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE refund_requests IS 'Yêu cầu hoàn theo đơn; số tiền do backend tính từ chính sách đã lưu.';

-- Giao dịch hoàn thực tế; chưa chốt chia nhiều lần hay hoàn gộp.
CREATE TABLE customer_refunds (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    refund_request_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    original_payment_id BIGINT,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    channel TEXT NOT NULL CHECK (channel IN ('payos','vnpay','bank_transfer')),
    transaction_reference VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    refunded_at TIMESTAMPTZ NOT NULL,
    recorded_by BIGINT NOT NULL REFERENCES app_accounts(id),
    evidence_url TEXT NOT NULL,
    FOREIGN KEY (refund_request_id, booking_id) REFERENCES refund_requests(id, booking_id),
    FOREIGN KEY (original_payment_id, booking_id) REFERENCES customer_payments(id, booking_id),
    UNIQUE (channel, transaction_reference),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE customer_refunds IS 'Giao dịch hoàn thực tế; chưa chốt chia nhiều lần hay hoàn gộp.';

-- Bảng tính tiền đối tác trong kỳ; cần có các dòng chi tiết, không suy đã chi từ đã duyệt.
CREATE TABLE company_statements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft','approved','paying','paid','failed')),
    prepared_by BIGINT NOT NULL REFERENCES app_accounts(id),
    approved_by BIGINT REFERENCES app_accounts(id),
    approved_at TIMESTAMPTZ,
    note TEXT,
    UNIQUE (company_id, period_start, period_end),
    UNIQUE (id, company_id),
    CHECK (period_end >= period_start),
    CHECK (status NOT IN ('approved','paying','paid') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE company_statements IS 'Bảng tính tiền đối tác trong kỳ; cần có các dòng chi tiết, không suy đã chi từ đã duyệt.';

-- Dòng tiền của từng đơn; điều chỉnh kỳ sau tham chiếu dòng gốc.
CREATE TABLE company_statement_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('sale','adjustment')),
    original_item_id BIGINT,
    retained_revenue_vnd BIGINT NOT NULL,
    platform_fee_vnd BIGINT NOT NULL,
    held_amount_vnd BIGINT NOT NULL DEFAULT 0 CHECK (held_amount_vnd >= 0),
    payable_vnd BIGINT GENERATED ALWAYS AS (retained_revenue_vnd - platform_fee_vnd - held_amount_vnd) STORED,
    reason TEXT,
    FOREIGN KEY (statement_id, company_id) REFERENCES company_statements(id, company_id),
    FOREIGN KEY (booking_id, company_id) REFERENCES bookings(id, company_id),
    UNIQUE (id, booking_id, company_id),
    FOREIGN KEY (original_item_id, booking_id, company_id) REFERENCES company_statement_items(id, booking_id, company_id),
    CHECK (original_item_id IS NULL OR original_item_id <> id),
    CHECK ((kind = 'sale' AND original_item_id IS NULL AND retained_revenue_vnd >= 0
       AND platform_fee_vnd BETWEEN 0 AND retained_revenue_vnd
       AND held_amount_vnd <= retained_revenue_vnd - platform_fee_vnd)
       OR (kind = 'adjustment' AND original_item_id IS NOT NULL AND NULLIF(btrim(reason),'') IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE company_statement_items IS 'Dòng tiền của từng đơn; điều chỉnh kỳ sau tham chiếu dòng gốc.';
CREATE UNIQUE INDEX uq_booking_initial_statement ON company_statement_items(booking_id) WHERE kind = 'sale';

-- Tiền thực tế chuyển đối tác; không lưu lần thử thất bại thành khoản đã chi.
CREATE TABLE company_transfers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    statement_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    amount_vnd BIGINT NOT NULL CHECK (amount_vnd > 0),
    bank_reference VARCHAR(200) NOT NULL UNIQUE,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    transferred_at TIMESTAMPTZ NOT NULL,
    recorded_by BIGINT NOT NULL REFERENCES app_accounts(id),
    evidence_url TEXT NOT NULL,
    FOREIGN KEY (statement_id, company_id) REFERENCES company_statements(id, company_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE company_transfers IS 'Tiền thực tế chuyển đối tác; không lưu lần thử thất bại thành khoản đã chi.';

-- Một bài mỗi đơn, hai điểm; tác giả phải là chủ đơn và có Participant tương ứng.
CREATE TABLE reviews (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    author_id BIGINT NOT NULL,
    participant_id BIGINT NOT NULL,
    tour_rating SMALLINT NOT NULL CHECK (tour_rating BETWEEN 1 AND 5),
    company_rating SMALLINT NOT NULL CHECK (company_rating BETWEEN 1 AND 5),
    content TEXT,
    equipment_feedback TEXT,
    image_urls JSONB NOT NULL DEFAULT '[]'::JSONB CHECK (jsonb_typeof(image_urls) = 'array'),
    status TEXT NOT NULL DEFAULT 'visible' CHECK (status IN ('visible','hidden')),
    moderated_by BIGINT REFERENCES app_accounts(id),
    moderation_reason TEXT,
    company_reply TEXT,
    replied_by BIGINT REFERENCES app_accounts(id),
    replied_at TIMESTAMPTZ,
    FOREIGN KEY (booking_id, author_id) REFERENCES bookings(id, customer_id),
    FOREIGN KEY (participant_id, booking_id, author_id) REFERENCES participants(id, booking_id, account_id),
    CHECK (status <> 'hidden' OR (moderated_by IS NOT NULL AND NULLIF(btrim(moderation_reason),'') IS NOT NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE reviews IS 'Một bài mỗi đơn, hai điểm; tác giả phải là chủ đơn và có Participant tương ứng.';

-- Mỗi tài khoản đánh dấu hữu ích một lần/bài.
CREATE TABLE review_votes (
    review_id BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES app_accounts(id),
    PRIMARY KEY (review_id, account_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE review_votes IS 'Mỗi tài khoản đánh dấu hữu ích một lần/bài.';

-- Yêu cầu hỗ trợ tiền/đơn; không phải yêu cầu cứu hộ.
CREATE TABLE support_requests (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requester_id BIGINT NOT NULL REFERENCES app_accounts(id),
    requester_company_id BIGINT REFERENCES companies(id),
    booking_id BIGINT REFERENCES bookings(id),
    statement_id BIGINT REFERENCES company_statements(id),
    payment_id BIGINT REFERENCES customer_payments(id),
    refund_request_id BIGINT REFERENCES refund_requests(id),
    subject VARCHAR(250) NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('booking','payment','refund','statement','other')),
    status TEXT NOT NULL DEFAULT 'new' CHECK (status IN ('new','assigned','waiting','resolved','closed')),
    assigned_to BIGINT REFERENCES app_accounts(id),
    CHECK (booking_id IS NOT NULL OR statement_id IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE support_requests IS 'Yêu cầu hỗ trợ tiền/đơn; không phải yêu cầu cứu hộ.';

-- Trao đổi và lịch sử xử lý yêu cầu.
CREATE TABLE support_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    support_request_id BIGINT NOT NULL REFERENCES support_requests(id),
    author_id BIGINT NOT NULL REFERENCES app_accounts(id),
    body TEXT NOT NULL,
    attachment_urls JSONB NOT NULL DEFAULT '[]'::JSONB CHECK (jsonb_typeof(attachment_urls) = 'array'),
    is_internal BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE support_messages IS 'Trao đổi và lịch sử xử lý yêu cầu.';

-- Mỗi dòng là một phiên bản tài liệu AI; bản mới tham chiếu bản trước.
CREATE TABLE ai_reference_documents (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(250) NOT NULL,
    file_url TEXT NOT NULL,
    file_type TEXT NOT NULL CHECK (file_type IN ('pdf','docx','txt')),
    version_number INTEGER NOT NULL DEFAULT 1 CHECK (version_number > 0),
    previous_version_id BIGINT REFERENCES ai_reference_documents(id),
    uploaded_by BIGINT NOT NULL REFERENCES app_accounts(id),
    policy_version_id BIGINT REFERENCES policy_versions(id),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','processing','ready','failed','archived')),
    processing_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE ai_reference_documents IS 'Mỗi dòng là một phiên bản tài liệu AI; bản mới tham chiếu bản trước.';

-- Hội thoại có chủ sở hữu; AI và Staff chỉ đọc dữ liệu đúng quyền.
CREATE TABLE chats (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_accounts(id),
    assigned_staff_id BIGINT REFERENCES app_accounts(id),
    current_tour_id BIGINT REFERENCES tours(id),
    current_booking_id BIGINT REFERENCES bookings(id),
    support_request_id BIGINT REFERENCES support_requests(id),
    status TEXT NOT NULL DEFAULT 'ai' CHECK (status IN ('ai','waiting_staff','staff','closed')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chats IS 'Hội thoại có chủ sở hữu; AI và Staff chỉ đọc dữ liệu đúng quyền.';

-- Tin nhắn trong hội thoại; tác giả là account hoặc AI/hệ thống.
CREATE TABLE chat_messages (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chat_id BIGINT NOT NULL REFERENCES chats(id),
    sender_type TEXT NOT NULL CHECK (sender_type IN ('user','staff','ai','system')),
    sender_id BIGINT REFERENCES app_accounts(id),
    body TEXT NOT NULL,
    CHECK ((sender_type IN ('user','staff') AND sender_id IS NOT NULL)
       OR (sender_type IN ('ai','system') AND sender_id IS NULL)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_messages IS 'Tin nhắn trong hội thoại; tác giả là account hoặc AI/hệ thống.';

-- Nguồn tài liệu của câu trả lời; quan hệ N:N.
CREATE TABLE chat_message_sources (
    message_id BIGINT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL REFERENCES ai_reference_documents(id),
    PRIMARY KEY (message_id, document_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_message_sources IS 'Nguồn tài liệu của câu trả lời; quan hệ N:N.';

-- Backend ghi lịch sử thao tác và đổi trạng thái; không ghi mật khẩu/token.
CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id BIGINT REFERENCES app_accounts(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT,
    reason TEXT,
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE audit_logs IS 'Backend ghi lịch sử thao tác và đổi trạng thái; không ghi mật khẩu/token.';

-- PHAN MO RONG: tao cau truc de thong nhat ten; chua bat buoc dua vao UI ban dau.

-- Câu hỏi trước mua; khác với đánh giá sau chuyến.
CREATE TABLE tour_questions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES tours(id),
    author_id BIGINT NOT NULL REFERENCES app_accounts(id),
    question TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'visible' CHECK (status IN ('visible','hidden')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_questions IS 'Câu hỏi trước mua; khác với đánh giá sau chuyến.';

-- Trả lời theo quyền ở backend; không tự cho mọi tài khoản quyền trả lời.
CREATE TABLE tour_answers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT NOT NULL REFERENCES tour_questions(id),
    author_id BIGINT NOT NULL REFERENCES app_accounts(id),
    answer TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'visible' CHECK (status IN ('visible','hidden')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE tour_answers IS 'Trả lời theo quyền ở backend; không tự cho mọi tài khoản quyền trả lời.';

-- DiscountCode/PriceOffer CHUA tao: nguon tai tro, cach tinh gia, so ma/Booking
-- chua chot. Them migration khi co rule; khong tao cot discount nhung quen tru tong tien.

CREATE INDEX ix_tours_public ON tours(status, created_at DESC);
CREATE INDEX ix_schedules_search ON tour_schedules(status, starts_at);
CREATE INDEX ix_bookings_expiry ON bookings(hold_expires_at) WHERE status = 'pending_payment';
CREATE INDEX ix_bookings_customer_time ON bookings(customer_id, created_at DESC);
CREATE INDEX ix_bookings_schedule_status ON bookings(schedule_id, status);
CREATE INDEX ix_payments_booking ON customer_payments(booking_id, received_at);
CREATE INDEX ix_refunds_booking ON customer_refunds(booking_id, refunded_at);
CREATE INDEX ix_support_queue ON support_requests(assigned_to, status, created_at);
CREATE INDEX ix_chat_messages_order ON chat_messages(chat_id, created_at, id);
CREATE INDEX ix_audit_entity ON audit_logs(entity_type, entity_id, occurred_at);


-- Index cho cac lien ket/duong truy van pho bien.
CREATE INDEX ix_companies_approved_by ON companies(approved_by);
CREATE INDEX ix_company_members_account_id ON company_members(account_id);
CREATE INDEX ix_guides_company_id ON guides(company_id);
CREATE INDEX ix_policy_versions_approved_by ON policy_versions(approved_by);
CREATE INDEX ix_company_agreements_policy_version_id ON company_agreements(policy_version_id);
CREATE INDEX ix_policies_company_id ON policies(company_id);
CREATE INDEX ix_tours_company_id ON tours(company_id);
CREATE INDEX ix_tours_cancellation_policy_version_id ON tours(cancellation_policy_version_id);
CREATE INDEX ix_tour_locations_location_id ON tour_locations(location_id);
CREATE INDEX ix_tour_media_tour_id ON tour_media(tour_id);
CREATE INDEX ix_tour_activities_tour_id ON tour_activities(tour_id);
CREATE INDEX ix_tour_required_items_tour_id ON tour_required_items(tour_id);
CREATE INDEX ix_tour_schedules_tour_id ON tour_schedules(tour_id);
CREATE INDEX ix_tour_schedules_company_id ON tour_schedules(company_id);
CREATE INDEX ix_schedule_guides_guide_id ON schedule_guides(guide_id);
CREATE INDEX ix_carts_schedule_id ON carts(schedule_id);
CREATE INDEX ix_bookings_company_id ON bookings(company_id);
CREATE INDEX ix_bookings_cancellation_policy_version_id ON bookings(cancellation_policy_version_id);
CREATE INDEX ix_participants_account_id ON participants(account_id);
CREATE INDEX ix_payment_attempts_booking_id ON payment_attempts(booking_id);
CREATE INDEX ix_payment_notices_attempt_id ON payment_notices(attempt_id);
CREATE INDEX ix_refund_requests_booking_id ON refund_requests(booking_id);
CREATE INDEX ix_customer_refunds_refund_request_id ON customer_refunds(refund_request_id);
CREATE INDEX ix_customer_refunds_original_payment_id ON customer_refunds(original_payment_id);
CREATE INDEX ix_company_statement_items_statement_id ON company_statement_items(statement_id);
CREATE INDEX ix_company_statement_items_original_item_id ON company_statement_items(original_item_id);
CREATE INDEX ix_company_statement_items_booking_id ON company_statement_items(booking_id);
CREATE INDEX ix_company_transfers_statement_id ON company_transfers(statement_id);
CREATE INDEX ix_reviews_author_id ON reviews(author_id);
CREATE INDEX ix_review_votes_account_id ON review_votes(account_id);
CREATE INDEX ix_support_requests_booking_id ON support_requests(booking_id);
CREATE INDEX ix_support_requests_statement_id ON support_requests(statement_id);
CREATE INDEX ix_support_messages_support_request_id ON support_messages(support_request_id);
CREATE INDEX ix_ai_reference_documents_policy_version_id ON ai_reference_documents(policy_version_id);
CREATE INDEX ix_chats_owner_id ON chats(owner_id);
CREATE INDEX ix_chat_message_sources_document_id ON chat_message_sources(document_id);
CREATE INDEX ix_tour_questions_tour_id ON tour_questions(tour_id);
CREATE INDEX ix_tour_answers_question_id ON tour_answers(question_id);

-- Cap nhat updated_at tu dong; optimistic lock_version do backend quan ly.
CREATE FUNCTION set_updated_at() RETURNS TRIGGER LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_app_accounts_updated BEFORE UPDATE ON app_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_account_roles_updated BEFORE UPDATE ON account_roles FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_companies_updated BEFORE UPDATE ON companies FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_company_members_updated BEFORE UPDATE ON company_members FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_guides_updated BEFORE UPDATE ON guides FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_policies_updated BEFORE UPDATE ON policies FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_policy_versions_updated BEFORE UPDATE ON policy_versions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_company_agreements_updated BEFORE UPDATE ON company_agreements FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_locations_updated BEFORE UPDATE ON locations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tours_updated BEFORE UPDATE ON tours FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_locations_updated BEFORE UPDATE ON tour_locations FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_media_updated BEFORE UPDATE ON tour_media FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_activities_updated BEFORE UPDATE ON tour_activities FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_equipment_packages_updated BEFORE UPDATE ON tour_equipment_packages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_required_items_updated BEFORE UPDATE ON tour_required_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_schedules_updated BEFORE UPDATE ON tour_schedules FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_schedule_guides_updated BEFORE UPDATE ON schedule_guides FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_carts_updated BEFORE UPDATE ON carts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_bookings_updated BEFORE UPDATE ON bookings FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_participants_updated BEFORE UPDATE ON participants FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payment_attempts_updated BEFORE UPDATE ON payment_attempts FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_payment_notices_updated BEFORE UPDATE ON payment_notices FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_customer_payments_updated BEFORE UPDATE ON customer_payments FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_refund_requests_updated BEFORE UPDATE ON refund_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_customer_refunds_updated BEFORE UPDATE ON customer_refunds FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_company_statements_updated BEFORE UPDATE ON company_statements FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_company_statement_items_updated BEFORE UPDATE ON company_statement_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_company_transfers_updated BEFORE UPDATE ON company_transfers FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_reviews_updated BEFORE UPDATE ON reviews FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_review_votes_updated BEFORE UPDATE ON review_votes FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_support_requests_updated BEFORE UPDATE ON support_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_support_messages_updated BEFORE UPDATE ON support_messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_ai_reference_documents_updated BEFORE UPDATE ON ai_reference_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_chats_updated BEFORE UPDATE ON chats FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_chat_messages_updated BEFORE UPDATE ON chat_messages FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_chat_message_sources_updated BEFORE UPDATE ON chat_message_sources FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_questions_updated BEFORE UPDATE ON tour_questions FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_tour_answers_updated BEFORE UPDATE ON tour_answers FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Gia/chinh sach da co tien thu khong duoc ghi de.
CREATE FUNCTION guard_booking_snapshot() RETURNS TRIGGER LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.price_locked_at IS NOT NULL AND ROW(
        NEW.customer_id, NEW.schedule_id, NEW.company_id, NEW.participant_count,
        NEW.includes_equipment, NEW.base_price_vnd, NEW.equipment_fee_vnd,
        NEW.deposit_percent, NEW.platform_fee_percent, NEW.cancellation_policy_version_id,
        NEW.company_agreement_id, NEW.policy_snapshot, NEW.equipment_snapshot,
        NEW.balance_due_at, NEW.price_locked_at
    ) IS DISTINCT FROM ROW(
        OLD.customer_id, OLD.schedule_id, OLD.company_id, OLD.participant_count,
        OLD.includes_equipment, OLD.base_price_vnd, OLD.equipment_fee_vnd,
        OLD.deposit_percent, OLD.platform_fee_percent, OLD.cancellation_policy_version_id,
        OLD.company_agreement_id, OLD.policy_snapshot, OLD.equipment_snapshot,
        OLD.balance_due_at, OLD.price_locked_at
    ) THEN
        RAISE EXCEPTION 'Booking da co tien thu: khong duoc doi gia, lich, so nguoi, chinh sach';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_booking_snapshot BEFORE UPDATE ON bookings
FOR EACH ROW EXECUTE FUNCTION guard_booking_snapshot();

CREATE FUNCTION lock_paid_booking() RETURNS TRIGGER LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    UPDATE bookings SET price_locked_at = COALESCE(price_locked_at, CURRENT_TIMESTAMP)
    WHERE id = NEW.booking_id;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_locks_booking AFTER INSERT ON customer_payments
FOR EACH ROW EXECUTE FUNCTION lock_paid_booking();

-- Khoan tien da thuc thu/hoan/chi khong xoa. Sua sai bang nghiep vu doi chieu co audit.
CREATE FUNCTION prevent_money_delete() RETURNS TRIGGER LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'Khong xoa giao dich tien thuc te; can dieu chinh co lich su';
END;
$$;
CREATE TRIGGER trg_payment_no_delete BEFORE DELETE ON customer_payments
FOR EACH ROW EXECUTE FUNCTION prevent_money_delete();
CREATE TRIGGER trg_refund_no_delete BEFORE DELETE ON customer_refunds
FOR EACH ROW EXECUTE FUNCTION prevent_money_delete();
CREATE TRIGGER trg_transfer_no_delete BEFORE DELETE ON company_transfers
FOR EACH ROW EXECUTE FUNCTION prevent_money_delete();

-- Policy da duoc tham chieu phai tao version moi thay vi sua noi dung cu.
CREATE FUNCTION protect_policy_version() RETURNS TRIGGER LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM company_agreements WHERE policy_version_id = OLD.id)
       OR EXISTS (SELECT 1 FROM bookings WHERE cancellation_policy_version_id = OLD.id)
       OR EXISTS (SELECT 1 FROM tours WHERE cancellation_policy_version_id = OLD.id) THEN
        RAISE EXCEPTION 'Policy version dang duoc su dung; hay tao version moi';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_policy_version_immutable BEFORE UPDATE OR DELETE ON policy_versions
FOR EACH ROW EXECUTE FUNCTION protect_policy_version();

-- Kiem dieu kien khi tao review; FK o tren da dam bao chu don = tac gia = Participant.
-- Sau nay sua check-in/hoan thanh phai duoc audit va xu ly review lien quan o backend.
CREATE FUNCTION validate_review() RETURNS TRIGGER LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
DECLARE b_status TEXT; s_status TEXT; attended_at TIMESTAMPTZ;
BEGIN
    SELECT b.status, s.status, p.checked_in_at
    INTO b_status, s_status, attended_at
    FROM bookings b JOIN tour_schedules s ON s.id = b.schedule_id
    JOIN participants p ON p.id = NEW.participant_id AND p.booking_id = b.id
    WHERE b.id = NEW.booking_id;
    IF b_status IS DISTINCT FROM 'completed' OR s_status IS DISTINCT FROM 'completed'
       OR attended_at IS NULL THEN
        RAISE EXCEPTION 'Chi danh gia sau khi don/chuyen hoan thanh va tac gia da check-in';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_review_eligibility BEFORE INSERT OR UPDATE OF booking_id, participant_id, author_id
ON reviews FOR EACH ROW EXECUTE FUNCTION validate_review();

-- Views chi tong hop, khong xac nhan giao dich hoac cap quyen.
-- Bao ve so tien va danh tinh giao dich da ghi nhan.
CREATE FUNCTION guard_payment_update() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    IF ROW(NEW.booking_id, NEW.attempt_id, NEW.provider, NEW.transaction_reference,
           NEW.amount_vnd, NEW.received_at) IS DISTINCT FROM
       ROW(OLD.booking_id, OLD.attempt_id, OLD.provider, OLD.transaction_reference,
           OLD.amount_vnd, OLD.received_at) THEN
        RAISE EXCEPTION 'Khong ghi de khoan thu; xu ly sai lech co lich su';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payment_immutable BEFORE UPDATE ON customer_payments
FOR EACH ROW EXECUTE FUNCTION guard_payment_update();

CREATE FUNCTION prevent_record_change() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'Ban ghi da xac nhan: khong sua/xoa; can tao ban ghi dieu chinh';
END;
$$;
CREATE TRIGGER trg_refund_immutable BEFORE UPDATE ON customer_refunds
FOR EACH ROW EXECUTE FUNCTION prevent_record_change();
CREATE TRIGGER trg_transfer_immutable BEFORE UPDATE ON company_transfers
FOR EACH ROW EXECUTE FUNCTION prevent_record_change();
CREATE TRIGGER trg_agreement_immutable BEFORE UPDATE OR DELETE ON company_agreements
FOR EACH ROW EXECUTE FUNCTION prevent_record_change();
CREATE TRIGGER trg_audit_immutable BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_record_change();

-- Khoa dong Statement de sua item khong tranh chap voi thao tac duyet/chi.
CREATE FUNCTION guard_statement_item() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
DECLARE statement_status TEXT;
BEGIN
    IF TG_OP = 'UPDATE' AND ROW(NEW.statement_id, NEW.booking_id, NEW.company_id, NEW.kind, NEW.original_item_id)
       IS DISTINCT FROM ROW(OLD.statement_id, OLD.booking_id, OLD.company_id, OLD.kind, OLD.original_item_id) THEN
        RAISE EXCEPTION 'Khong chuyen dong doi soat sang don/ky khac';
    END IF;
    SELECT status INTO statement_status FROM company_statements
    WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.statement_id ELSE NEW.statement_id END
    FOR UPDATE;
    IF statement_status IS DISTINCT FROM 'draft' THEN
        RAISE EXCEPTION 'Chi sua chi tiet khi bang doi soat con la draft';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_statement_item_guard BEFORE INSERT OR UPDATE OR DELETE ON company_statement_items
FOR EACH ROW EXECUTE FUNCTION guard_statement_item();

CREATE FUNCTION guard_paid_statement() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.status = 'paid' THEN
        RAISE EXCEPTION 'Giu nguyen ky da chi; dieu chinh vao ky sau';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status <> 'draft' AND NEW.status = 'draft' THEN
        RAISE EXCEPTION 'Khong mo lai ky da duyet bang cach doi ve draft';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_statement_paid_guard BEFORE UPDATE OR DELETE ON company_statements
FOR EACH ROW EXECUTE FUNCTION guard_paid_statement();

CREATE VIEW booking_money_totals AS
SELECT b.id AS booking_id, b.total_price_vnd,
       COALESCE(p.received, 0) AS received_vnd,
       COALESCE(p.accepted, 0) AS accepted_vnd,
       COALESCE(r.refunded, 0) AS refunded_vnd
FROM bookings b
LEFT JOIN (SELECT booking_id, SUM(amount_vnd) AS received,
    SUM(amount_vnd) FILTER (WHERE allocation_status = 'accepted') AS accepted
    FROM customer_payments GROUP BY booking_id) p ON p.booking_id = b.id
LEFT JOIN (SELECT booking_id, SUM(amount_vnd) AS refunded
    FROM customer_refunds GROUP BY booking_id) r ON r.booking_id = b.id;
COMMENT ON VIEW booking_money_totals IS
'Tổng thu/hoàn để tra cứu. Hoàn khoản thu dư/pending_review cần phân loại ở backend trước tính doanh thu/hoa hồng; Các tổng này không thay thế phép tính doanh thu giữ lại/hoa hồng.';

CREATE VIEW company_statement_totals AS
SELECT s.id AS statement_id, s.company_id,
       COALESCE(i.payable, 0) AS payable_vnd,
       COALESCE(t.transferred, 0) AS transferred_vnd,
       COALESCE(i.payable, 0) - COALESCE(t.transferred, 0) AS remaining_vnd
FROM company_statements s
LEFT JOIN (SELECT statement_id, SUM(payable_vnd) AS payable
    FROM company_statement_items GROUP BY statement_id) i ON i.statement_id = s.id
LEFT JOIN (SELECT statement_id, SUM(amount_vnd) AS transferred
    FROM company_transfers GROUP BY statement_id) t ON t.statement_id = s.id;

-- Khong seed tai khoan admin/mat khau mac dinh. Backend tao account va hash an toan.
-- Xem bang: SELECT table_name FROM information_schema.tables WHERE table_schema='trekking';
-- Hibernate: dung validate (khong ddl-auto=update); schema mac dinh cua app = trekking.
COMMIT;
