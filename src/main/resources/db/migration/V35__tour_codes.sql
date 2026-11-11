-- Admin-managed tour codes used as the booking-code prefix.
-- Codes are soft-deleted and never reused, so historical booking codes stay unambiguous.
CREATE TABLE trekking.tour_codes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tour_id BIGINT NOT NULL REFERENCES trekking.tours(id),
    code VARCHAR(20) NOT NULL CHECK (code ~ '^[A-Z0-9]{2,20}$'),
    created_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT REFERENCES trekking.app_accounts(id),
    delete_reason TEXT,
    CHECK ((deleted_at IS NULL) = (deleted_by IS NULL)),
    CHECK (deleted_at IS NULL OR btrim(coalesce(delete_reason, '')) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE trekking.tour_codes IS 'Mã tour do admin quản lý; xoá mềm, không dùng lại mã đã xoá.';

CREATE UNIQUE INDEX uq_tour_codes_code ON trekking.tour_codes(code);
CREATE UNIQUE INDEX uq_tour_codes_active_tour ON trekking.tour_codes(tour_id) WHERE deleted_at IS NULL;
CREATE TRIGGER trg_tour_codes_updated BEFORE UPDATE ON trekking.tour_codes
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
