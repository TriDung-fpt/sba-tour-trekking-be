-- Apply ONCE after 006. Existing free-text packing/package descriptions are kept.
-- This adds structured tour equipment configuration; stock, allocation and Booking integration remain out of scope.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE trekking.equipment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_equipment_name_ci ON trekking.equipment (lower(btrim(name)));
COMMENT ON TABLE trekking.equipment IS 'Danh mục loại dụng cụ; không lưu tồn kho.';

-- Support databases initialized from either the old or renamed bootstrap table.
DO $$
BEGIN
    IF to_regclass('trekking.tour_required_items') IS NULL
       AND to_regclass('trekking.packing_items') IS NOT NULL THEN
        ALTER TABLE trekking.packing_items RENAME TO tour_required_items;
    END IF;
END;
$$;

-- Link existing checklist rows to the catalog when convenient. NULL keeps old free-text rows valid.
ALTER TABLE trekking.tour_required_items
    ADD COLUMN equipment_id BIGINT REFERENCES trekking.equipment(id) ON DELETE SET NULL;
CREATE INDEX ix_tour_required_items_equipment_id ON trekking.tour_required_items(equipment_id);
COMMENT ON COLUMN trekking.tour_required_items.equipment_id IS 'Optional catalog link; the existing name remains for checklist display and legacy rows.';
COMMENT ON TABLE trekking.tour_required_items IS 'Đồ khách cần chuẩn bị; có thể liên kết danh mục dụng cụ nhưng không quản lý tồn kho.';

-- Keep the current one-package-per-tour model. NULL means its rental price has not been configured.
ALTER TABLE trekking.tour_equipment_packages
    ADD COLUMN rental_price_vnd BIGINT CHECK (rental_price_vnd >= 0);
COMMENT ON COLUMN trekking.tour_equipment_packages.rental_price_vnd IS 'Package rental fee in VND for one participant for the whole tour; NULL means not configured.';
COMMENT ON TABLE trekking.tour_equipment_packages IS 'Một gói dụng cụ cho mỗi tour; giá NULL nghĩa là chưa cấu hình; không quản lý tồn kho.';
COMMENT ON COLUMN trekking.tour_equipment_packages.included_items IS 'Mô tả text cũ được giữ để tương thích; danh sách dụng cụ có cấu trúc lưu tại tour_equipment_items.';

CREATE TABLE trekking.tour_equipment_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    package_id BIGINT NOT NULL REFERENCES trekking.tour_equipment_packages(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES trekking.equipment(id) ON DELETE RESTRICT,
    quantity_in_package INTEGER NOT NULL DEFAULT 1 CHECK (quantity_in_package > 0),
    individual_rental_price_vnd BIGINT CHECK (individual_rental_price_vnd >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tour_equipment_items_package_equipment UNIQUE (package_id, equipment_id)
);
CREATE INDEX ix_tour_equipment_items_equipment_id ON trekking.tour_equipment_items(equipment_id);
COMMENT ON TABLE trekking.tour_equipment_items IS 'Các món có trong gói dụng cụ của tour; giá thuê lẻ NULL nghĩa là không cho thuê lẻ.';
COMMENT ON COLUMN trekking.tour_equipment_items.quantity_in_package IS 'Số lượng món này có trong một gói dụng cụ.';
COMMENT ON COLUMN trekking.tour_equipment_items.individual_rental_price_vnd IS 'Giá thuê lẻ một món cho một khách trong cả tour; NULL nghĩa là không cho thuê lẻ.';

CREATE TRIGGER trg_equipment_updated BEFORE UPDATE ON trekking.equipment
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_tour_equipment_items_updated BEFORE UPDATE ON trekking.tour_equipment_items
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();

COMMIT;
