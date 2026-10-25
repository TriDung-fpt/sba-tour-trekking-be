-- Extend the existing paid-booking snapshot guard to cover group rental pricing.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE OR REPLACE FUNCTION trekking.guard_booking_snapshot() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.price_locked_at IS NOT NULL AND ROW(
        NEW.customer_id, NEW.schedule_id, NEW.participant_count, NEW.includes_equipment,
        NEW.base_price_vnd, NEW.equipment_fee_vnd, NEW.group_rental_fee_vnd, NEW.deposit_percent,
        NEW.cancellation_policy_version_id, NEW.policy_snapshot, NEW.equipment_snapshot,
        NEW.balance_due_at, NEW.price_locked_at
    ) IS DISTINCT FROM ROW(
        OLD.customer_id, OLD.schedule_id, OLD.participant_count, OLD.includes_equipment,
        OLD.base_price_vnd, OLD.equipment_fee_vnd, OLD.group_rental_fee_vnd, OLD.deposit_percent,
        OLD.cancellation_policy_version_id, OLD.policy_snapshot, OLD.equipment_snapshot,
        OLD.balance_due_at, OLD.price_locked_at
    ) THEN
        RAISE EXCEPTION 'Paid booking price, schedule, participants and snapshots are immutable';
    END IF;
    RETURN NEW;
END;
$$;

COMMIT;
