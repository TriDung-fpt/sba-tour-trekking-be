-- Apply after 002, before deploying agreement/policy APIs. No legacy fee defaults
-- are inferred: publish a new approved version with explicit structured terms.
BEGIN;
ALTER TABLE trekking.policy_versions
    ADD COLUMN platform_fee_percent NUMERIC(5,2),
    ADD COLUMN payout_wait_hours INTEGER,
    ADD COLUMN payout_terms_snapshot TEXT,
    ADD CONSTRAINT ck_policy_fee CHECK (platform_fee_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT ck_policy_wait CHECK (payout_wait_hours >= 0),
    ADD CONSTRAINT ck_policy_terms CHECK (payout_terms_snapshot IS NULL OR btrim(payout_terms_snapshot) <> ''),
    ADD CONSTRAINT ck_policy_financial_terms_complete CHECK (
        (platform_fee_percent IS NULL AND payout_wait_hours IS NULL AND payout_terms_snapshot IS NULL)
        OR (platform_fee_percent IS NOT NULL AND payout_wait_hours IS NOT NULL AND payout_terms_snapshot IS NOT NULL));

CREATE OR REPLACE FUNCTION trekking.protect_policy_version() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.approved_at IS NOT NULL
       OR EXISTS (SELECT 1 FROM company_agreements WHERE policy_version_id = OLD.id)
       OR EXISTS (SELECT 1 FROM bookings WHERE cancellation_policy_version_id = OLD.id)
       OR EXISTS (SELECT 1 FROM tours WHERE cancellation_policy_version_id = OLD.id) THEN
        RAISE EXCEPTION 'Approved or referenced policy version is immutable; publish a new version';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
COMMIT;
