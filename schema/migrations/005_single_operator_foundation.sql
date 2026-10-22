-- Apply after 004 with the application stopped, before deploying single-operator code.
-- This migration deliberately preserves companies, agreements, paid booking snapshots
-- and money history. Existing volumes are NOT upgraded by Compose init scripts.
-- Back up the database before upgrading. Recovery requires restoring that backup
-- together with the previous application; archived roles must not be reactivated
-- automatically. No account is promoted to admin by this migration.
BEGIN;
SET LOCAL lock_timeout = '10s';

CREATE TABLE trekking.legacy_account_roles (
    account_id BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    role TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role)
);

CREATE TABLE trekking.legacy_account_financial_permissions (
    account_id BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    permission TEXT NOT NULL,
    granted_by BIGINT NOT NULL REFERENCES trekking.app_accounts(id),
    granted_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, permission)
);

INSERT INTO trekking.legacy_account_roles (account_id, role, created_at, updated_at)
SELECT account_id, role, created_at, updated_at
FROM trekking.account_roles
WHERE role NOT IN ('admin', 'guide', 'customer');

INSERT INTO trekking.legacy_account_financial_permissions (account_id, permission, granted_by, granted_at)
SELECT account_id, permission, granted_by, granted_at
FROM trekking.account_financial_permissions;

-- Replace BEFORE deleting accountant roles: the old role trigger deleted grants.
-- Keep authorization revisions for every subsequent role change, independently of
-- the removed accountant role and its now historical grants.
CREATE OR REPLACE FUNCTION trekking.invalidate_role_challenges() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE app_accounts SET authorization_version = authorization_version + 1 WHERE id = OLD.account_id;
        RETURN OLD;
    END IF;
    UPDATE app_accounts SET authorization_version = authorization_version + 1 WHERE id = NEW.account_id;
    IF TG_OP = 'UPDATE' AND OLD.account_id <> NEW.account_id THEN
        UPDATE app_accounts SET authorization_version = authorization_version + 1 WHERE id = OLD.account_id;
    END IF;
    RETURN NEW;
END;
$$;

DELETE FROM trekking.account_roles WHERE role NOT IN ('admin', 'guide', 'customer');

-- Preserve access at the least privileged role only for affected accounts that
-- otherwise have no supported role. Existing admin/guide/customer roles survive.
INSERT INTO trekking.account_roles (account_id, role)
SELECT DISTINCT archived.account_id, 'customer'
FROM trekking.legacy_account_roles archived
WHERE NOT EXISTS (SELECT 1 FROM trekking.account_roles existing_role
                  WHERE existing_role.account_id = archived.account_id);

-- Invalidate both JWT and refresh sessions that could contain removed roles.
UPDATE trekking.app_accounts account
SET session_version = session_version + 1
WHERE EXISTS (SELECT 1 FROM trekking.legacy_account_roles archived WHERE archived.account_id = account.id);

ALTER TABLE trekking.account_roles
    DROP CONSTRAINT account_roles_role_check,
    ADD CONSTRAINT account_roles_role_check CHECK (role IN ('admin', 'guide', 'customer'));

-- ADMIN has internal finance permissions in application policy. Legacy grants
-- remain readable for historical inspection and can never grant current access.
CREATE OR REPLACE FUNCTION trekking.validate_financial_permission() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'Financial permission grants are retired; internal finance requires admin role'
        USING ERRCODE = '23514';
END;
$$;

CREATE FUNCTION trekking.reject_legacy_authorization_write() RETURNS TRIGGER
LANGUAGE plpgsql SET search_path = trekking, pg_temp AS $$
BEGIN
    RAISE EXCEPTION 'Legacy authorization records are read-only' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_financial_permission_no_delete BEFORE DELETE
ON trekking.account_financial_permissions FOR EACH ROW EXECUTE FUNCTION trekking.reject_legacy_authorization_write();
CREATE TRIGGER trg_legacy_roles_read_only BEFORE INSERT OR UPDATE OR DELETE
ON trekking.legacy_account_roles FOR EACH ROW EXECUTE FUNCTION trekking.reject_legacy_authorization_write();
CREATE TRIGGER trg_legacy_financial_permissions_read_only BEFORE INSERT OR UPDATE OR DELETE
ON trekking.legacy_account_financial_permissions FOR EACH ROW EXECUTE FUNCTION trekking.reject_legacy_authorization_write();

-- NULL denotes a new internally owned record. Do not backfill or rewrite legacy
-- company IDs: paid booking snapshots include those IDs and are immutable.
ALTER TABLE trekking.guides ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE trekking.tours ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE trekking.tour_schedules ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE trekking.schedule_guides ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE trekking.bookings
    ALTER COLUMN company_id DROP NOT NULL,
    ALTER COLUMN platform_fee_percent SET DEFAULT 0;

-- MATCH SIMPLE composite FKs skip validation when company_id is NULL. Preserve
-- their legacy company consistency checks AND add scalar FKs for internal rows.
ALTER TABLE trekking.tour_schedules
    ADD CONSTRAINT fk_schedules_tour FOREIGN KEY (tour_id) REFERENCES trekking.tours(id);
ALTER TABLE trekking.schedule_guides
    ADD CONSTRAINT fk_schedule_guides_schedule FOREIGN KEY (schedule_id) REFERENCES trekking.tour_schedules(id),
    ADD CONSTRAINT fk_schedule_guides_guide FOREIGN KEY (guide_id) REFERENCES trekking.guides(id);
ALTER TABLE trekking.bookings
    ADD CONSTRAINT fk_bookings_schedule FOREIGN KEY (schedule_id) REFERENCES trekking.tour_schedules(id),
    ADD CONSTRAINT fk_bookings_agreement FOREIGN KEY (company_agreement_id) REFERENCES trekking.company_agreements(id),
    ADD CONSTRAINT ck_internal_booking_no_agreement CHECK (company_id IS NOT NULL OR company_agreement_id IS NULL);

COMMENT ON TABLE trekking.legacy_account_roles IS 'Read-only roles retired by migration 005; never interpreted as current access.';
COMMENT ON TABLE trekking.legacy_account_financial_permissions IS 'Read-only grant archive captured before retiring accountant authorization.';
COMMENT ON TABLE trekking.account_financial_permissions IS 'Read-only historical grants. Single-operator finance uses ADMIN role only.';
COMMENT ON COLUMN trekking.guides.company_id IS 'Legacy company association only; NULL for internal guides.';
COMMENT ON COLUMN trekking.tours.company_id IS 'Legacy company association only; NULL for internal tours.';
COMMENT ON COLUMN trekking.tour_schedules.company_id IS 'Legacy company association only; NULL for internal departures.';
COMMENT ON COLUMN trekking.schedule_guides.company_id IS 'Legacy company association only; NULL for internal assignments.';
COMMENT ON COLUMN trekking.bookings.company_id IS 'Preserved legacy booking snapshot; NULL for internal bookings.';

-- Migration 003 already protects approved versions and versions referenced by
-- bookings, tours or agreements. Retain that trigger and all money/snapshot guards.
COMMIT;
