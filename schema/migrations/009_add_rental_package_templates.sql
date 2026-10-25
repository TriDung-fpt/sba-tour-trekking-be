-- Reusable equipment and rental-package catalog used by the tour admin UI.
BEGIN;
SET LOCAL lock_timeout = '10s';

ALTER TABLE trekking.equipment
    ADD COLUMN individual_rental_price_vnd BIGINT CHECK (individual_rental_price_vnd >= 0);

CREATE TABLE trekking.rental_package_templates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (btrim(title) <> ''),
    shared_items TEXT,
    personal_items_to_bring TEXT,
    rental_price_vnd BIGINT NOT NULL CHECK (rental_price_vnd >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE trekking.rental_package_template_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    package_template_id BIGINT NOT NULL REFERENCES trekking.rental_package_templates(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES trekking.equipment(id) ON DELETE RESTRICT,
    quantity_in_package INTEGER NOT NULL DEFAULT 1 CHECK (quantity_in_package > 0),
    individual_rental_price_vnd BIGINT CHECK (individual_rental_price_vnd >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rental_package_template_equipment UNIQUE (package_template_id, equipment_id)
);
CREATE INDEX ix_rental_package_template_items_equipment ON trekking.rental_package_template_items(equipment_id);

ALTER TABLE trekking.tour_equipment_packages
    ADD COLUMN package_template_id BIGINT REFERENCES trekking.rental_package_templates(id) ON DELETE RESTRICT;
CREATE INDEX ix_tour_equipment_packages_template_id ON trekking.tour_equipment_packages(package_template_id);

CREATE TRIGGER trg_rental_package_templates_updated BEFORE UPDATE ON trekking.rental_package_templates
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_rental_package_template_items_updated BEFORE UPDATE ON trekking.rental_package_template_items
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();

COMMENT ON TABLE trekking.rental_package_templates IS 'Mẫu gói thuê dùng lại cho nhiều tour; tour lưu bản sao cấu hình để giữ nguyên hợp đồng thuê hiện có.';
COMMENT ON COLUMN trekking.tour_equipment_packages.package_template_id IS 'Mẫu gói gốc được chọn; thông tin giá và món trong tour được lưu thành bản sao.';
COMMENT ON COLUMN trekking.equipment.individual_rental_price_vnd IS 'Giá thuê lẻ mặc định cho một món trong một tour; có thể được ghi đè trong mẫu gói.';

COMMIT;
