-- EQ-01..EQ-08: equipment stock, reservations and handover history.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE trekking.equipment_categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (btrim(name) <> ''),
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_equipment_categories_name_ci ON trekking.equipment_categories (lower(btrim(name)));

CREATE TABLE trekking.equipment_inventory (
    equipment_id BIGINT PRIMARY KEY REFERENCES trekking.equipment(id) ON DELETE RESTRICT,
    category_id BIGINT REFERENCES trekking.equipment_categories(id) ON DELETE RESTRICT,
    total_quantity INTEGER NOT NULL DEFAULT 0 CHECK (total_quantity >= 0),
    maintenance_quantity INTEGER NOT NULL DEFAULT 0 CHECK (maintenance_quantity >= 0),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (maintenance_quantity <= total_quantity)
);
CREATE INDEX ix_equipment_inventory_category_id ON trekking.equipment_inventory(category_id);

CREATE TABLE trekking.equipment_reservations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES trekking.bookings(id) ON DELETE RESTRICT,
    schedule_id BIGINT NOT NULL REFERENCES trekking.tour_schedules(id) ON DELETE RESTRICT,
    package_id BIGINT REFERENCES trekking.tour_equipment_packages(id) ON DELETE RESTRICT,
    selection_key TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('held','confirmed','released','issued','return_required','returned')),
    participant_count INTEGER NOT NULL CHECK (participant_count > 0),
    rental_fee_vnd BIGINT NOT NULL DEFAULT 0 CHECK (rental_fee_vnd BETWEEN 0 AND 1000000000000),
    hold_expires_at TIMESTAMPTZ NOT NULL,
    equipment_snapshot JSONB NOT NULL DEFAULT '{}'::JSONB CHECK (jsonb_typeof(equipment_snapshot) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_equipment_reservations_schedule_status ON trekking.equipment_reservations(schedule_id, status, hold_expires_at);

CREATE TABLE trekking.equipment_reservation_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES trekking.equipment_reservations(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES trekking.equipment(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    issued_quantity INTEGER NOT NULL DEFAULT 0 CHECK (issued_quantity >= 0),
    returned_quantity INTEGER NOT NULL DEFAULT 0 CHECK (returned_quantity >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (reservation_id, equipment_id),
    CHECK (issued_quantity <= quantity),
    CHECK (returned_quantity <= issued_quantity)
);
CREATE INDEX ix_equipment_reservation_items_equipment ON trekking.equipment_reservation_items(equipment_id);

CREATE TABLE trekking.equipment_handover_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES trekking.equipment_reservations(id) ON DELETE RESTRICT,
    action TEXT NOT NULL CHECK (action IN ('issued','returned')),
    actor_id BIGINT NOT NULL REFERENCES trekking.app_accounts(id) ON DELETE RESTRICT,
    note TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_equipment_handover_records_reservation ON trekking.equipment_handover_records(reservation_id, occurred_at);

CREATE TABLE trekking.equipment_handover_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    handover_record_id BIGINT NOT NULL REFERENCES trekking.equipment_handover_records(id) ON DELETE CASCADE,
    equipment_id BIGINT NOT NULL REFERENCES trekking.equipment(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    condition TEXT NOT NULL CHECK (condition IN ('good','needs_inspection')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_equipment_handover_items_record ON trekking.equipment_handover_items(handover_record_id);

CREATE TRIGGER trg_equipment_categories_updated BEFORE UPDATE ON trekking.equipment_categories
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_equipment_inventory_updated BEFORE UPDATE ON trekking.equipment_inventory
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_equipment_reservations_updated BEFORE UPDATE ON trekking.equipment_reservations
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_equipment_reservation_items_updated BEFORE UPDATE ON trekking.equipment_reservation_items
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_equipment_handover_records_updated BEFORE UPDATE ON trekking.equipment_handover_records
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_equipment_handover_items_updated BEFORE UPDATE ON trekking.equipment_handover_items
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();

COMMIT;
