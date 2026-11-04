BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE trekking.refund_rule_sets (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    effective_from TIMESTAMPTZ NOT NULL,
    rules JSONB NOT NULL CHECK (jsonb_typeof(rules) = 'array' AND jsonb_array_length(rules) > 0),
    created_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER trg_refund_rule_sets_updated BEFORE UPDATE ON trekking.refund_rule_sets
    FOR EACH ROW EXECUTE FUNCTION trekking.set_updated_at();

ALTER TABLE trekking.bookings ADD COLUMN refund_rules_snapshot JSONB;
ALTER TABLE trekking.bookings DROP CONSTRAINT IF EXISTS bookings_refund_rules_snapshot_check;
ALTER TABLE trekking.bookings ADD CONSTRAINT bookings_refund_rules_snapshot_check
    CHECK (refund_rules_snapshot IS NULL OR jsonb_typeof(refund_rules_snapshot) = 'array');

CREATE FUNCTION trekking.guard_booking_refund_rules_snapshot() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.price_locked_at IS NOT NULL AND NEW.refund_rules_snapshot IS DISTINCT FROM OLD.refund_rules_snapshot THEN
        RAISE EXCEPTION 'Booking da co tien thu: khong duoc doi snapshot muc hoan';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_booking_refund_rules_snapshot BEFORE UPDATE ON trekking.bookings
    FOR EACH ROW EXECUTE FUNCTION trekking.guard_booking_refund_rules_snapshot();

ALTER TABLE trekking.refund_requests
    ADD COLUMN bank_bin VARCHAR(20),
    ADD COLUMN recipient_name_ciphertext TEXT,
    ADD COLUMN recipient_account_ciphertext TEXT,
    ADD COLUMN recipient_account_last4 VARCHAR(4),
    ADD COLUMN recipient_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN payout_reference_id VARCHAR(100),
    ADD COLUMN payout_id VARCHAR(200),
    ADD COLUMN payout_transaction_reference VARCHAR(200),
    ADD COLUMN payout_last_checked_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_refund_payout_reference ON trekking.refund_requests(payout_reference_id)
    WHERE payout_reference_id IS NOT NULL;
CREATE INDEX ix_refund_requests_processing ON trekking.refund_requests(status, updated_at)
    WHERE status IN ('approved', 'processing');

ALTER TABLE trekking.customer_refunds
    ADD COLUMN payout_id VARCHAR(200);
ALTER TABLE trekking.customer_refunds ALTER COLUMN evidence_url DROP NOT NULL;
CREATE UNIQUE INDEX uq_customer_refunds_payout_id ON trekking.customer_refunds(channel, payout_id)
    WHERE payout_id IS NOT NULL;

COMMIT;
