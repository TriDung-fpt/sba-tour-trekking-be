-- Existing equipment remains available; deleted equipment is retained for historical records.
ALTER TABLE trekking.equipment ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
