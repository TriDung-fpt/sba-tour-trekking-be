-- Guide batch check-in / check-out rounds, participant attendance statuses, and per-participant equipment handover with condition photos.

SET LOCAL lock_timeout = '10s';

CREATE TABLE trekking.schedule_attendance_batches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES trekking.tour_schedules(id) ON DELETE RESTRICT,
    round_type TEXT NOT NULL CHECK (round_type IN ('CHECK_IN', 'CHECK_OUT')),
    round_status TEXT NOT NULL DEFAULT 'CONFIRMED' CHECK (round_status IN ('CONFIRMED')),
    idempotency_key VARCHAR(120) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    submitted_by_account_id BIGINT NOT NULL REFERENCES trekking.app_accounts(id) ON DELETE RESTRICT,
    submitted_by_guide_id BIGINT NOT NULL REFERENCES trekking.guides(id) ON DELETE RESTRICT,
    general_note TEXT CHECK (char_length(general_note) <= 2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (schedule_id, round_type)
);

CREATE INDEX ix_schedule_attendance_batches_schedule ON trekking.schedule_attendance_batches(schedule_id);

CREATE TABLE trekking.schedule_participant_attendances (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES trekking.tour_schedules(id) ON DELETE RESTRICT,
    booking_id BIGINT NOT NULL REFERENCES trekking.bookings(id) ON DELETE RESTRICT,
    participant_id BIGINT NOT NULL UNIQUE REFERENCES trekking.participants(id) ON DELETE RESTRICT,
    check_in_batch_id BIGINT REFERENCES trekking.schedule_attendance_batches(id) ON DELETE RESTRICT,
    check_in_status TEXT NOT NULL CHECK (check_in_status IN ('PRESENT', 'ABSENT')),
    check_in_marked_at TIMESTAMPTZ NOT NULL,
    check_in_note TEXT CHECK (char_length(check_in_note) <= 1000),
    check_out_batch_id BIGINT REFERENCES trekking.schedule_attendance_batches(id) ON DELETE RESTRICT,
    check_out_status TEXT CHECK (check_out_status IN ('COMPLETED', 'EARLY_DEPARTURE', 'ABSENT_AT_CHECKIN')),
    check_out_marked_at TIMESTAMPTZ,
    check_out_note TEXT CHECK (char_length(check_out_note) <= 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_schedule_participant_attendances_schedule ON trekking.schedule_participant_attendances(schedule_id);
CREATE INDEX ix_schedule_participant_attendances_booking ON trekking.schedule_participant_attendances(booking_id);

CREATE TABLE trekking.participant_equipment_handovers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    schedule_id BIGINT NOT NULL REFERENCES trekking.tour_schedules(id) ON DELETE RESTRICT,
    booking_id BIGINT NOT NULL REFERENCES trekking.bookings(id) ON DELETE RESTRICT,
    participant_id BIGINT NOT NULL REFERENCES trekking.participants(id) ON DELETE RESTRICT,
    equipment_id BIGINT NOT NULL REFERENCES trekking.equipment(id) ON DELETE RESTRICT,
    allocated_quantity INTEGER NOT NULL CHECK (allocated_quantity > 0),
    check_in_condition TEXT NOT NULL CHECK (check_in_condition IN ('GOOD', 'NEEDS_INSPECTION', 'DAMAGED', 'MISSING')),
    check_in_note TEXT CHECK (char_length(check_in_note) <= 1000),
    check_in_photos_json JSONB NOT NULL DEFAULT '[]'::JSONB CHECK (jsonb_typeof(check_in_photos_json) = 'array'),
    handed_over_at TIMESTAMPTZ NOT NULL,
    returned_quantity INTEGER CHECK (returned_quantity >= 0 AND returned_quantity <= allocated_quantity),
    check_out_condition TEXT CHECK (check_out_condition IN ('GOOD', 'NEEDS_INSPECTION', 'DAMAGED', 'MISSING')),
    check_out_note TEXT CHECK (char_length(check_out_note) <= 1000),
    check_out_photos_json JSONB NOT NULL DEFAULT '[]'::JSONB CHECK (jsonb_typeof(check_out_photos_json) = 'array'),
    returned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (schedule_id, participant_id, equipment_id)
);

CREATE INDEX ix_participant_equipment_handovers_schedule ON trekking.participant_equipment_handovers(schedule_id);
CREATE INDEX ix_participant_equipment_handovers_participant ON trekking.participant_equipment_handovers(participant_id);

CREATE TRIGGER trg_schedule_attendance_batches_updated BEFORE UPDATE ON trekking.schedule_attendance_batches
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_schedule_participant_attendances_updated BEFORE UPDATE ON trekking.schedule_participant_attendances
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
CREATE TRIGGER trg_participant_equipment_handovers_updated BEFORE UPDATE ON trekking.participant_equipment_handovers
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();
