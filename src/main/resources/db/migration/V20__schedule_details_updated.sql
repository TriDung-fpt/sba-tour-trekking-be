
ALTER TABLE trekking.tour_schedules ADD COLUMN details_updated_at TIMESTAMPTZ;
UPDATE trekking.tour_schedules SET details_updated_at = updated_at;
ALTER TABLE trekking.tour_schedules ALTER COLUMN details_updated_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE trekking.tour_schedules ALTER COLUMN details_updated_at SET NOT NULL;

CREATE FUNCTION trekking.set_schedule_details_updated_at() RETURNS trigger AS $$
BEGIN
    IF ROW(NEW.starts_at, NEW.ends_at, NEW.capacity, NEW.base_price_vnd, NEW.equipment_fee_vnd,
           NEW.deposit_percent, NEW.balance_due_at, NEW.meeting_point, NEW.contact_phone)
       IS DISTINCT FROM
       ROW(OLD.starts_at, OLD.ends_at, OLD.capacity, OLD.base_price_vnd, OLD.equipment_fee_vnd,
           OLD.deposit_percent, OLD.balance_due_at, OLD.meeting_point, OLD.contact_phone) THEN
        NEW.details_updated_at := CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER tour_schedule_details_updated BEFORE UPDATE ON trekking.tour_schedules
    FOR EACH ROW EXECUTE FUNCTION trekking.set_schedule_details_updated_at();
