-- Apply ONCE after 005, with all application writers stopped and a verified backup.
-- This is a contract migration: old application versions cannot run afterwards.
-- Existing Docker volumes do not rerun init scripts. No company or settlement
-- object remains in the active trekking schema; historical evidence is copied to
-- legacy_archive, without active foreign keys or application entity mappings.
-- Recovery after COMMIT requires the database backup and its matching application.
BEGIN;
SET LOCAL lock_timeout = '10s';

-- Never rewrite a paid booking's policy reference merely to remove cooperation.
-- These references were invalid under the customer cancellation-policy contract;
-- an operator must review them before this migration can proceed.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM trekking.policy_versions version
        JOIN trekking.policies policy ON policy.id = version.policy_id
        WHERE policy.kind = 'cooperation' AND (
            EXISTS (SELECT 1 FROM trekking.bookings booking WHERE booking.cancellation_policy_version_id = version.id)
            OR EXISTS (SELECT 1 FROM trekking.tours tour WHERE tour.cancellation_policy_version_id = version.id)
            OR EXISTS (SELECT 1 FROM trekking.ai_reference_documents document WHERE document.policy_version_id = version.id)
        )
    ) THEN
        RAISE EXCEPTION 'Cannot remove cooperation policy referenced by booking, tour or AI document; review references before migration'
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE SCHEMA legacy_archive;
REVOKE ALL ON SCHEMA legacy_archive FROM PUBLIC;
COMMENT ON SCHEMA legacy_archive IS 'Immutable migration evidence only; excluded from application entities and runtime policy.';

CREATE TABLE legacy_archive.snapshot_manifest (
    source_table TEXT PRIMARY KEY,
    row_count BIGINT NOT NULL CHECK (row_count >= 0),
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE FUNCTION legacy_archive.reject_write() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Archived migration evidence is read-only' USING ERRCODE = '23514';
END;
$$;

-- Full original rows preserve fee snapshots, agreement evidence, actual payouts,
-- prior guide associations and support/review metadata before columns disappear.
-- CTAS intentionally does not copy foreign keys into the active application schema.
DO $$
DECLARE source_name TEXT; archived_count BIGINT;
BEGIN
    FOREACH source_name IN ARRAY ARRAY[
        'companies', 'company_members', 'company_agreements', 'company_statements',
        'company_statement_items', 'company_transfers', 'account_financial_permissions',
        'legacy_account_roles', 'legacy_account_financial_permissions', 'guides',
        'policies', 'policy_versions', 'tours', 'tour_schedules', 'schedule_guides',
        'bookings', 'support_requests', 'reviews'
    ] LOOP
        EXECUTE format('CREATE TABLE legacy_archive.%I AS TABLE trekking.%I WITH DATA', source_name, source_name);
        EXECUTE format('SELECT count(*) FROM legacy_archive.%I', source_name) INTO archived_count;
        INSERT INTO legacy_archive.snapshot_manifest(source_table, row_count) VALUES (source_name, archived_count);
        EXECUTE format('CREATE TRIGGER trg_archive_read_only BEFORE INSERT OR UPDATE OR DELETE ON legacy_archive.%I '
            || 'FOR EACH ROW EXECUTE FUNCTION legacy_archive.reject_write()', source_name);
    END LOOP;
END;
$$;
CREATE TRIGGER trg_archive_manifest_read_only BEFORE INSERT OR UPDATE OR DELETE
ON legacy_archive.snapshot_manifest FOR EACH ROW EXECUTE FUNCTION legacy_archive.reject_write();

-- Keep formerly agreed versions immutable without inventing approval timestamps
-- or retaining an active dependency on agreements or archived company records.
ALTER TABLE trekking.policy_versions ADD COLUMN locked_for_history BOOLEAN NOT NULL DEFAULT FALSE;
DROP TRIGGER trg_policy_version_immutable ON trekking.policy_versions;
UPDATE trekking.policy_versions version SET locked_for_history = TRUE
WHERE EXISTS (SELECT 1 FROM trekking.company_agreements agreement WHERE agreement.policy_version_id = version.id);

DELETE FROM trekking.policy_versions version USING trekking.policies policy
WHERE version.policy_id = policy.id AND policy.kind = 'cooperation';
DELETE FROM trekking.policies WHERE kind = 'cooperation';
ALTER TABLE trekking.policies
    DROP CONSTRAINT policies_kind_check,
    ADD CONSTRAINT policies_kind_check CHECK (kind IN ('cancellation', 'payment', 'general'));

CREATE OR REPLACE FUNCTION trekking.protect_policy_version() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.locked_for_history OR OLD.approved_at IS NOT NULL
       OR EXISTS (SELECT 1 FROM bookings WHERE cancellation_policy_version_id = OLD.id)
       OR EXISTS (SELECT 1 FROM tours WHERE cancellation_policy_version_id = OLD.id) THEN
        RAISE EXCEPTION 'Published or historically referenced policy version is immutable; publish a new version';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_policy_version_immutable BEFORE UPDATE OR DELETE ON trekking.policy_versions
FOR EACH ROW EXECUTE FUNCTION trekking.protect_policy_version();

-- The removed metadata is retained exactly in legacy_archive.bookings. Remaining
-- customer price, schedule, participant count, policy and equipment snapshots stay
-- locked after the first real payment, including price_locked_at itself.
CREATE OR REPLACE FUNCTION trekking.guard_booking_snapshot() RETURNS TRIGGER LANGUAGE plpgsql
SET search_path = trekking, pg_temp AS $$
BEGIN
    IF OLD.price_locked_at IS NOT NULL AND ROW(
        NEW.customer_id, NEW.schedule_id, NEW.participant_count, NEW.includes_equipment,
        NEW.base_price_vnd, NEW.equipment_fee_vnd, NEW.deposit_percent,
        NEW.cancellation_policy_version_id, NEW.policy_snapshot, NEW.equipment_snapshot,
        NEW.balance_due_at, NEW.price_locked_at
    ) IS DISTINCT FROM ROW(
        OLD.customer_id, OLD.schedule_id, OLD.participant_count, OLD.includes_equipment,
        OLD.base_price_vnd, OLD.equipment_fee_vnd, OLD.deposit_percent,
        OLD.cancellation_policy_version_id, OLD.policy_snapshot, OLD.equipment_snapshot,
        OLD.balance_due_at, OLD.price_locked_at
    ) THEN
        RAISE EXCEPTION 'Paid booking price, schedule, participants and snapshots are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP VIEW trekking.company_statement_totals;
DROP TABLE trekking.company_transfers;
DROP TABLE trekking.company_statement_items;

-- Preserve statement-only conversations without inventing a customer booking.
-- Their original statement IDs/category and requester association are archived.
ALTER TABLE trekking.support_requests
    DROP CONSTRAINT support_requests_check,
    DROP CONSTRAINT support_requests_category_check;
UPDATE trekking.support_requests SET category = 'other' WHERE category = 'statement';
ALTER TABLE trekking.support_requests
    DROP COLUMN requester_company_id,
    DROP COLUMN statement_id,
    ADD CONSTRAINT support_requests_category_check CHECK (category IN ('booking', 'payment', 'refund', 'other')),
    ADD CONSTRAINT ck_support_request_context CHECK (booking_id IS NOT NULL OR category = 'other');

-- Child composite keys must go first. The direct scalar foreign keys introduced
-- by 005 remain in place, so removing company IDs never weakens referential integrity.
ALTER TABLE trekking.schedule_guides DROP COLUMN company_id;
ALTER TABLE trekking.bookings
    DROP COLUMN company_id,
    DROP COLUMN company_agreement_id,
    DROP COLUMN platform_fee_percent;
ALTER TABLE trekking.tour_schedules DROP COLUMN company_id;
ALTER TABLE trekking.guides DROP COLUMN company_id;
ALTER TABLE trekking.tours DROP COLUMN company_id;
ALTER TABLE trekking.policies DROP COLUMN company_id;
ALTER TABLE trekking.policy_versions
    DROP COLUMN platform_fee_percent,
    DROP COLUMN payout_wait_hours,
    DROP COLUMN payout_terms_snapshot;

ALTER TABLE trekking.reviews RENAME COLUMN company_rating TO service_rating;
ALTER TABLE trekking.reviews RENAME COLUMN company_reply TO operator_reply;
ALTER TABLE trekking.reviews RENAME CONSTRAINT reviews_company_rating_check TO reviews_service_rating_check;

DROP TABLE trekking.company_agreements;
DROP TABLE trekking.company_statements;
DROP TABLE trekking.company_members;
DROP TABLE trekking.companies;
DROP TABLE trekking.account_financial_permissions;
DROP TABLE trekking.legacy_account_financial_permissions;
DROP TABLE trekking.legacy_account_roles;
DROP FUNCTION trekking.guard_statement_item();
DROP FUNCTION trekking.guard_paid_statement();
DROP FUNCTION trekking.validate_financial_permission();
DROP FUNCTION trekking.reject_legacy_authorization_write();

COMMENT ON TABLE trekking.guides IS 'System guide profile linked to an account; access requires current GUIDE role, active profile and assignment.';
COMMENT ON TABLE trekking.policies IS 'Versioned customer cancellation, payment and general policies of the operator.';
COMMENT ON TABLE trekking.tours IS 'Tour products designed and managed by the operator.';
COMMENT ON TABLE trekking.schedule_guides IS 'System guide assignments to departures.';
COMMENT ON TABLE trekking.bookings IS 'Customer booking with immutable paid price and customer policy snapshots.';
COMMENT ON COLUMN trekking.policy_versions.locked_for_history IS 'Historical references require this version to remain immutable, independently of approval timestamps.';
COMMENT ON VIEW trekking.booking_money_totals IS 'Actual customer receipts and refunds; aggregate lookup does not replace transactional payment and refund checks.';
COMMIT;
