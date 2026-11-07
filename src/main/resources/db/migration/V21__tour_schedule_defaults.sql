
CREATE TABLE trekking.tour_schedule_defaults (
    tour_id BIGINT PRIMARY KEY REFERENCES trekking.tours(id),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    base_price_vnd BIGINT NOT NULL CHECK (base_price_vnd BETWEEN 0 AND 1000000000000),
    equipment_fee_vnd BIGINT NOT NULL CHECK (equipment_fee_vnd BETWEEN 0 AND 1000000000000),
    deposit_percent NUMERIC(5,2) NOT NULL CHECK (deposit_percent > 0 AND deposit_percent <= 100),
    meeting_point TEXT,
    contact_phone VARCHAR(30)
);
