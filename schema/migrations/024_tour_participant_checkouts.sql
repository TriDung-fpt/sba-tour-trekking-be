-- Guide records the end-of-trip headcount once per participant.
BEGIN;
CREATE TABLE trekking.tour_participant_checkouts (
    participant_id BIGINT PRIMARY KEY REFERENCES trekking.participants(id) ON DELETE RESTRICT,
    checked_out_at TIMESTAMPTZ NOT NULL,
    checked_out_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id) ON DELETE RESTRICT,
    note TEXT CHECK (char_length(note) <= 1000)
);
COMMIT;
